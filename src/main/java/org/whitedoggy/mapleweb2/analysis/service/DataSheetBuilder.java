package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.dto.DataSheetResponse;
import org.whitedoggy.mapleweb2.analysis.support.SupportMethods;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.analysis.dto.CharacterSnapshot;
import org.whitedoggy.mapleweb2.domain.calculator.data.CommonStatSheetView;
import org.whitedoggy.mapleweb2.domain.calculator.data.PresetSelection;
import org.whitedoggy.mapleweb2.domain.calculator.data.StatSheetComparisonResponse;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.cash.CashItemParser;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.hexa.HexaParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemParser;
import org.whitedoggy.mapleweb2.domain.pet.PetParser;
import org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser;
import org.whitedoggy.mapleweb2.domain.skill.SkillParser;
import org.whitedoggy.mapleweb2.domain.symbol.SymbolParser;
import org.whitedoggy.mapleweb2.domain.union.artifact.ArtifactParser;
import org.whitedoggy.mapleweb2.domain.union.champion.ChampionParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.whitedoggy.mapleweb2.domain.common.stat.JobStatTable.JOBS;

@Service
@RequiredArgsConstructor
public class DataSheetBuilder {
    private final NexonApiClient nexonApiClient;
    private final BasicParser basicParser;
    private final StatParser statParser;
    private final ItemEquipmentParser itemEquipmentParser;
    private final ItemParser itemParser;
    private final AbilityParser abilityParser;
    private final HyperStatParser hyperStatParser;
    private final RaiderParser raiderParser;
    private final SymbolParser symbolParser;
    private final PetParser petParser;
    private final SkillParser skillParser;
    private final ArtifactParser artifactParser;
    private final ChampionParser championParser;
    private final SetEffectParser setEffectParser;
    private final StatSheetParser statSheetParser;
    private final HexaParser hexaParser;
    private final CashItemParser cashItemParser;

    public Mono<DataSheetResponse> getStatSheets(String characterName, LocalDate date) {
        return nexonApiClient.getOcid(characterName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("캐릭터 OCID를 조회할 수 없습니다: " + characterName)))
                .flatMap(ocidResponse -> nexonApiClient.fetchSnapshot(ocidResponse.ocid(), date))
                .map(snapshot -> buildDataSheet(snapshot));
    }

    private DataSheetResponse buildDataSheet(CharacterSnapshot snapshot) {
        DataSheet dataSheet = new DataSheet();

        //기본 정보
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        dataSheet.setOcid(snapshot.ocid());
        dataSheet.setDate(snapshot.date());
        setBasics(dataSheet, basic);

        //AP(어빌리티 포인트)
        JsonNode stat = snapshot.document(NexonEndpoint.STAT);
        dataSheet.setAbilityPoint(setAP(stat));

        //심볼
        JsonNode symbol = snapshot.document(NexonEndpoint.SYMBOL_EQUIPMENT);

        //스킬(0차)
        JsonNode skill = snapshot.document(NexonEndpoint.SKILL_0);

        //헥사 스텟
        JsonNode hexa = snapshot.document(NexonEndpoint.HEXA_MATRIX_STAT);

        //어빌리티
        JsonNode ability = snapshot.document(NexonEndpoint.ABILITY);

        //하이퍼 스탯
        JsonNode hyper = snapshot.document(NexonEndpoint.HYPER_STAT);

        //펫 장비
        JsonNode petEquip = snapshot.document(NexonEndpoint.PET_EQUIPMENT);

        //캐시 장비
        JsonNode cashEquip = snapshot.document(NexonEndpoint.CASH_ITEM_EQUIPMENT);

        //프리셋 구분



        JsonNode unionRaider = snapshot.document(NexonEndpoint.UNION_RAIDER);

        String characterClass = basicParser.characterClass(basic);
        Integer characterLevel = basicParser.characterLevel(basic);
        List<String> mainStat = JOBS.get(characterClass).mainStats();
        List<String> subStat = JOBS.get(characterClass).subStats();

        /*
        CommonStatSheetView sharedView = buildSharedView(snapshot, mainStat, characterClass, characterLevel);

        PresetSelection currentPreset = new PresetSelection(
                itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(1),
                abilityParser.getCurrentPresetAbility(ability),
                hyperStatParser.getCurrentPresetNo(hyperStat),
                chooseCurrentUnionPreset(unionRaider)
        );

        PresetSelection combatPreset = new PresetSelection(
                chooseItemPreset(itemEquipment),
                chooseAbilityPreset(ability, characterClass),
                chooseHyperPreset(hyperStat),
                chooseUnionPreset(unionRaider)
        );

        Long apiCombatPower = Optional.ofNullable(statParser.currentCombatPower(stat))
                .map(Jsons::parseDouble)
                .map(Math::floor)
                .map(Double::longValue)
                .orElse(null);

        return new StatSheetComparisonResponse(
                characterName,
                characterClass,
                date,
                sharedView,
                buildPresetView(snapshot, sharedView.total(), characterClass, currentPreset, apiCombatPower, mainStat, subStat, characterLevel),
                buildPresetView(snapshot, sharedView.total(), characterClass, combatPreset, null, mainStat, subStat, characterLevel)
        );
         */
        return new DataSheetResponse(
                dataSheet
        );
    }

    private void setBasics(DataSheet dataSheet, JsonNode node){
        dataSheet.setCharacterName(basicParser.characterName(node));
        dataSheet.setCharacterClass(basicParser.characterClass(node));
        dataSheet.setCharacterLevel(basicParser.characterLevel(node));
        dataSheet.setCharacterImage(basicParser.characterWorld(node));
        dataSheet.setCharacterWorld(basicParser.characterWorld(node));
        dataSheet.setCharacterGuild(basicParser.characterGuild(node));
    }

    private StatSheet setAP(JsonNode node){
        StatSheet abilityPoint = new StatSheet("어빌리티 포인트");

        JsonNode stats = node.path("final_stat");
        for(JsonNode stat : stats){
            String statName = Jsons.text(stat, "stat_name");
            if(statName.equals("AP 배분 STR")){
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setSTR(statValue);
            }
            if(statName.equals("AP 배분 DEX")){
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setDEX(statValue);
            }
            if(statName.equals("AP 배분 LUK")){
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setLUK(statValue);
            }
            if(statName.equals("AP 배분 INT")){
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setINT(statValue);
            }
            if(statName.equals("AP 배분 HP")){
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setHP(statValue);
            }
        }
        return abilityPoint;
    }
}
