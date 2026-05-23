package org.whitedoggy.mapleweb2.domain.calculator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.domain.calculator.data.CombatTrendPoint;
import org.whitedoggy.mapleweb2.domain.calculator.data.CombatTrendResponse;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CombatTrendService {
    private static final String STAT_DAMAGE = "데미지";
    private static final String STAT_BOSS_DAMAGE = "보스 몬스터 데미지";
    private static final String STAT_CRITICAL_DAMAGE = "크리티컬 데미지";
    private static final String STAT_FINAL_DAMAGE = "최종 데미지";
    private static final String STAT_ATTACK = "공격력";
    private static final String STAT_MAGIC = "마력";
    private static final String CLASS_DEMON_AVENGER = "데몬어벤져";

    private final NexonApiClient nexonApiClient;
    private final BasicParser basicParser;
    private final StatParser statParser;
    private final ItemEquipmentParser itemEquipmentParser;
    private final AbilityParser abilityParser;
    private final HyperStatParser hyperStatParser;
    private final RaiderParser raiderParser;

    public Mono<CombatTrendResponse> getCombatTrend(String characterName) {
        return nexonApiClient.getOcid(characterName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("캐릭터 OCID를 조회할 수 없습니다: " + characterName)))
                .flatMap(ocidResponse -> Mono.zip(
                        fetchTrend(ocidResponse.ocid(), buildRecentDates()),
                        fetchTrend(ocidResponse.ocid(), buildYearlyDates()),
                        nexonApiClient.fetchCurrentSnapshot(ocidResponse.ocid(), LocalDate.now())
                ).map(tuple -> {
                    CharacterSnapshot currentSnapshot = tuple.getT3();
                    String characterClass = currentSnapshot == null ? "" : basicParser.characterClass(currentSnapshot.document(NexonEndpoint.BASIC));
                    return new CombatTrendResponse(characterName, characterClass, tuple.getT1(), tuple.getT2());
                }));
    }

    private Mono<List<CombatTrendPoint>> fetchTrend(String ocid, List<LocalDate> dates) {
        return Flux.fromIterable(dates)
                .concatMap(date -> nexonApiClient.fetchSnapshot(ocid, date))
                .map(this::toPoint)
                .collectSortedList(Comparator.comparing(CombatTrendPoint::date));
    }

    private CombatTrendPoint toPoint(CharacterSnapshot snapshot) {
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        JsonNode stat = snapshot.document(NexonEndpoint.STAT);
        Map<String, String> finalStats = statParser.finalStats(stat);
        long apiCombatPower = Long.parseLong(statParser.currentCombatPower(stat));
        long estimatedCombatPower = estimateCombatPower(basicParser.characterClass(basic), finalStats);

        PresetSelection selectedPresets = new PresetSelection(
                chooseItemPreset(snapshot.document(NexonEndpoint.ITEM_EQUIPMENT)),
                chooseAbilityPreset(snapshot.document(NexonEndpoint.ABILITY), basicParser.characterClass(basic)),
                chooseHyperPreset(snapshot.document(NexonEndpoint.HYPER_STAT)),
                chooseUnionPreset(snapshot.document(NexonEndpoint.UNION_RAIDER))
        );

        return new CombatTrendPoint(snapshot.date(), apiCombatPower, estimatedCombatPower, selectedPresets);
    }

    private int chooseItemPreset(JsonNode itemEquipment) {
        return itemEquipmentParser.availablePresets(itemEquipment).stream()
                .max(Comparator.<Integer>comparingInt(preset -> itemEquipmentParser.scorePreset(itemEquipmentParser.getItemEquipmentByPreset(itemEquipment, preset)))
                        .thenComparingInt(preset -> preset == itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(-1) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(1));
    }

    private int chooseAbilityPreset(JsonNode ability, String characterClass) {
        return abilityParser.availablePresets(ability).stream()
                .max(Comparator.<Integer>comparingInt(preset -> abilityParser.scorePreset(ability, preset, characterClass))
                        .thenComparingInt(preset -> preset == abilityParser.getCurrentPresetAbility(ability) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(abilityParser.getCurrentPresetAbility(ability));
    }

    private int chooseHyperPreset(JsonNode hyperStat) {
        return hyperStatParser.availablePresets(hyperStat).stream()
                .max(Comparator.<Integer>comparingInt(preset -> hyperStatParser.scorePreset(hyperStat, preset))
                        .thenComparingInt(preset -> preset == hyperStatParser.getCurrentPresetNo(hyperStat) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(hyperStatParser.getCurrentPresetNo(hyperStat));
    }

    private int chooseUnionPreset(JsonNode raider) {
        return raiderParser.availablePresets(raider).stream()
                .max(Comparator.<Integer>comparingInt(preset -> raiderParser.scorePreset(raider, preset))
                        .thenComparingInt(preset -> -preset))
                .orElse(1);
    }

    private long estimateCombatPower(String characterClass, Map<String, String> finalStats) {
        double mainStat;
        double subStat;
        double attackOrMagic = chooseAttackOrMagic(characterClass, finalStats);
        double damage = 100 + value(finalStats, STAT_DAMAGE) + value(finalStats, STAT_BOSS_DAMAGE);
        double criticalDamage = 135 + value(finalStats, STAT_CRITICAL_DAMAGE);
        double finalDamage = Math.max(100, value(finalStats, STAT_FINAL_DAMAGE));

        if (characterClass.contains(CLASS_DEMON_AVENGER)) {
            mainStat = value(finalStats, "HP");
            subStat = value(finalStats, "STR");
        } else {
            List<Map.Entry<String, Double>> stats = List.of(
                    Map.entry("STR", value(finalStats, "STR")),
                    Map.entry("DEX", value(finalStats, "DEX")),
                    Map.entry("INT", value(finalStats, "INT")),
                    Map.entry("LUK", value(finalStats, "LUK"))
            );
            Map.Entry<String, Double> main = stats.stream().max(Map.Entry.comparingByValue()).orElse(Map.entry("STR", 0.0));
            Map.Entry<String, Double> sub = stats.stream()
                    .filter(entry -> !entry.getKey().equals(main.getKey()))
                    .max(Map.Entry.comparingByValue())
                    .orElse(Map.entry("DEX", 0.0));
            mainStat = main.getValue();
            subStat = sub.getValue();
        }

        double statFactor = (mainStat * 4 + subStat) / 100.0;
        return (long) Math.floor(statFactor * attackOrMagic * damage * criticalDamage * finalDamage / 1_000_000.0);
    }

    private double chooseAttackOrMagic(String characterClass, Map<String, String> finalStats) {
        double attack = value(finalStats, STAT_ATTACK);
        double magic = value(finalStats, STAT_MAGIC);
        if (isMageClass(characterClass)) {
            return magic;
        }
        return Math.max(attack, magic);
    }

    private boolean isMageClass(String characterClass) {
        return characterClass.contains("비숍")
                || characterClass.contains("아크메이지")
                || characterClass.contains("불독")
                || characterClass.contains("썬콜")
                || characterClass.contains("일리움")
                || characterClass.contains("라라")
                || characterClass.contains("루미너스")
                || characterClass.contains("배틀메이지")
                || characterClass.contains("에반")
                || characterClass.contains("키네시스");
    }

    private double value(Map<String, String> values, String key) {
        return Jsons.parseDouble(values.get(key));
    }

    private List<LocalDate> buildRecentDates() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = new ArrayList<>();
        for (int days = 0; days <= 30; days += 2) {
            dates.add(today.minusDays(days));
        }
        return dates;
    }

    private List<LocalDate> buildYearlyDates() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = new ArrayList<>();
        for (int monthOffset = 0; monthOffset < 12; monthOffset++) {
            LocalDate month = today.minusMonths(monthOffset);
            dates.add(month.withDayOfMonth(Math.min(today.getDayOfMonth(), month.lengthOfMonth())));
            dates.add(month.withDayOfMonth(Math.min(15, month.lengthOfMonth())));
        }
        return dates.stream().distinct().sorted().toList();
    }
}
 