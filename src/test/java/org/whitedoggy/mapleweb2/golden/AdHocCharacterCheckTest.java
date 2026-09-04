package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.CombatCalculationService;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import java.util.List;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** 픽스처에 없는 캐릭터 한 명을 즉석에서 확인한다. -Dadhoc.fixture=<경로> */
@SpringBootTest
@EnabledIfSystemProperty(named = "adhoc.fixture", matches = ".+")
class AdHocCharacterCheckTest {

    private static final Map<String, NexonEndpoint> KEYS = Map.ofEntries(
            Map.entry("basic", NexonEndpoint.BASIC),
            Map.entry("stat", NexonEndpoint.STAT),
            Map.entry("item-equipment", NexonEndpoint.ITEM_EQUIPMENT),
            Map.entry("cashitem-equipment", NexonEndpoint.CASH_ITEM_EQUIPMENT),
            Map.entry("set-effect", NexonEndpoint.SET_EFFECT),
            Map.entry("symbol-equipment", NexonEndpoint.SYMBOL_EQUIPMENT),
            Map.entry("pet-equipment", NexonEndpoint.PET_EQUIPMENT),
            Map.entry("hyper-stat", NexonEndpoint.HYPER_STAT),
            Map.entry("ability", NexonEndpoint.ABILITY),
            Map.entry("skill", NexonEndpoint.SKILL_0),
            Map.entry("hexamatrix-stat", NexonEndpoint.HEXA_MATRIX_STAT),
            Map.entry("other-stat", NexonEndpoint.OTHER_STAT),
            Map.entry("union-raider", NexonEndpoint.UNION_RAIDER),
            Map.entry("union-champion", NexonEndpoint.UNION_CHAMPION),
            Map.entry("union-artifact", NexonEndpoint.UNION_ARTIFACT)
    );

    @Autowired private DataSheetService dataSheetService;
    @Autowired private CombatCalculationService combatCalculationService;
    @Autowired private BasicParser basicParser;
    @Autowired private GameData gameData;
    @Autowired private StatParser statParser;
    @Autowired private ObjectMapper mapper;
    @Autowired private org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser setEffectParser;
    @Autowired private org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser itemEquipmentParser;

    @Test
    void check() throws Exception {
        Path target = Path.of(System.getProperty("adhoc.fixture"));
        if (Files.isDirectory(target)) {
            try (var files = Files.list(target)) {
                for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".json.gz")).sorted().toList()) {
                    evaluate(p);
                }
            }
            return;
        }
        evaluate(target);
    }

    private void evaluate(Path path) throws Exception {
        JsonNode payload;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(path))) {
            payload = mapper.readTree(in);
        }
        Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(NexonEndpoint.class);
        KEYS.forEach((k, e) -> documents.put(e, payload.path("documents").path(k)));
        CharacterSnapshot snapshot = new CharacterSnapshot(
                payload.path("ocid").asText(),
                LocalDate.parse(payload.path("collectedAt").asText()),
                documents);

        DataSheet sheet = dataSheetService.getCurrentDataSheet(snapshot);
        String apiText = statParser.currentCombatPower(documents.get(NexonEndpoint.STAT));
        long api = apiText == null ? 0L : (long) Math.floor(Jsons.parseDouble(apiText));
        long calc = sheet.getCombatPower();

        System.out.printf("%n[adhoc] %s %s (Lv.%s)%n",
                payload.path("job").asText(), payload.path("characterName").asText(),
                payload.path("level").asText());
        System.out.printf("  계산 %,d / API %,d / 오차 %.4f%%%n",
                calc, api, api == 0 ? 0.0 : Math.abs(calc - api) * 100.0 / api);
        System.out.printf("  플래그 미접속=%s 아티팩트만료=%d 유니온미반영=%s 캐시만료=%d 칭호만료=%s 펫장비만료=%d 루시드=%s 무기정규화실패=%s%n",
                sheet.isInactiveCharacter(),
                sheet.getExpiredArtifactCrystals(), sheet.isUnionRaiderDataMissing(),
                sheet.getExpiredCashItems(), sheet.isExpiredTitleOption(),
                sheet.getExpiredPetEquipments(), sheet.isLucidTransformSuspected(),
                sheet.isWeaponNormalizationFailed());

        // 제로는 무기(라즐리) 공격력이 전투력에 들어가는지 확실하지 않다.
        // 적용한 값과 뺀 값을 함께 내서 어느 쪽이 API와 붙는지 본다.
        if ("제로".equals(basicParser.characterClass(documents.get(NexonEndpoint.BASIC)))) {
            double[] e = new double[4];
            int i = 0;
            for (boolean weapon : new boolean[]{true, false}) {
                for (boolean astraBoss : new boolean[]{true, false}) {
                    long v = zeroVariant(snapshot, documents, weapon, astraBoss);
                    e[i++] = api == 0 ? 0.0 : (v - api) * 100.0 / api;
                }
            }
            System.out.printf("  [제로4] 무기O·보스O %.4f / 무기O·보스X %.4f / 무기X·보스O %.4f / 무기X·보스X %.4f%n",
                    e[0], e[1], e[2], e[3]);
        }

        // 유니온(공격대원+점령)을 빼고 다시 계산한다. 이 값이 API와 붙으면
        // 넥슨 쪽 stat 집계에서 유니온이 빠진 날의 데이터라는 뜻이다.
        // 유니온 구성요소를 하나씩 빼 본다. API와 정확히 맞는 조합이 있으면
        // 넥슨이 그 항목을 빼고 집계했다는 뜻이다.
        System.out.printf("[UNION]\t%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d%n",
                payload.path("characterName").asText(), api,
                unionVariant(snapshot, documents, false, false, false, false),
                unionVariant(snapshot, documents, true, false, false, false),
                unionVariant(snapshot, documents, false, true, false, false),
                unionVariant(snapshot, documents, false, false, true, false),
                unionVariant(snapshot, documents, false, false, false, true),
                unionVariant(snapshot, documents, true, true, false, false),
                unionVariant(snapshot, documents, true, true, true, true));

        // 소스를 하나씩 빼 본다. API와 정확히 맞는 게 있으면 그 소스가 원인이다.
        String[] srcNames = {"abilityPoint", "symbol", "skill", "hexaStat", "ability",
                "hyperStat", "otherStat", "setEffect", "consumableItem", "conversionStarforce"};
        StringBuilder srcLine = new StringBuilder();
        for (String src : srcNames) {
            srcLine.append('\t').append(withoutSource(snapshot, documents, src));
        }
        System.out.printf("[SRC]\t%s\t%d\t%d%s%n",
                payload.path("characterName").asText(), api, calc, srcLine);

        long bare = withoutUnion(snapshot, documents);
        // 계산값은 어긋나는데 유니온을 뺀 값이 API와 정수까지 같으면, 우리가 틀린 게 아니라
        // 넥슨 stat 집계에서 유니온이 빠진 것이다. 골든의 unionMissingFromApiValue와 같은 기준.
        boolean unionMissingFromApi = api != 0 && bare == api && calc != api;
        System.out.printf("  유니온제외 %,d / 오차 %.4f%%%s%n",
                bare, api == 0 ? 0.0 : Math.abs(bare - api) * 100.0 / api,
                unionMissingFromApi ? "  <<< API 전투력이 유니온을 빼고 집계됨" : "");

        // 공격력 항을 따로 본다. 오차가 이 항에만 있으면 Δ가 정수로 떨어진다.
        StatSheet sum = sheet.getSumSheet();
        boolean mage = gameData.isMageClass(basicParser.characterClass(documents.get(NexonEndpoint.BASIC)));
        int powerRaw = mage ? sum.getMAGIC_POWER() : sum.getATTACK_POWER();
        int powerPct = mage ? sum.getMAGIC_POWER_PERCENT() : sum.getATTACK_POWER_PERCENT();

        // 공식 입력값. 오차를 항별로 역산할 때 필요하다.
        List<String> mains = gameData.mainStats(basicParser.characterClass(documents.get(NexonEndpoint.BASIC)));
        List<String> subs = gameData.subStats(basicParser.characterClass(documents.get(NexonEndpoint.BASIC)));
        String mainName = mains.isEmpty() ? "" : mains.getFirst();
        String subName = subs.isEmpty() ? "" : subs.getFirst();

        // -Dadhoc.detail=이름[,이름...] 이면 그 캐릭터들의 부위별 파싱 결과를 원본과 나란히 찍는다.
        String detailNames = System.getProperty("adhoc.detail", "");
        if (!detailNames.isBlank()
                && List.of(detailNames.split(",")).contains(payload.path("characterName").asText())) {
            dumpItemDetail(sheet, documents.get(NexonEndpoint.ITEM_EQUIPMENT));
            dumpTermsBySource(sheet);
        }

        // 적용된 세트와 개수. API 의 set_effect 목록과 대조용.
        var presetSelection = dataSheetService.getCurrentPresetSelection(snapshot);
        var presetItems = itemEquipmentParser.getItemEquipmentByPreset(
                documents.get(NexonEndpoint.ITEM_EQUIPMENT), presetSelection.itemPreset());
        var appliedSets = setEffectParser.getAppliedSetCounts(
                documents.get(NexonEndpoint.SET_EFFECT), presetItems,
                basicParser.characterClass(documents.get(NexonEndpoint.BASIC)));
        System.out.printf("[SETS]\t%s\t%s%n", payload.path("characterName").asText(), appliedSets);

        // 기계 판독용 한 줄. TSV.
        // 제논(주스탯 3개)·데몬어벤져(주스탯 HP)는 mainName 하나로는 역산이 안 된다.
        // 네 스탯의 계산값과 HP 성분을 뒤에 붙인다.
        String extra = String.format("\t%.0f\t%.0f\t%.0f\t%.0f\t%d\t%d\t%d\t%.2f\t%.2f\t%.2f",
                statValue("STR", sum, level(documents)), statValue("DEX", sum, level(documents)),
                statValue("INT", sum, level(documents)), statValue("LUK", sum, level(documents)),
                sum.getHP(), sum.getHP_PERCENT(), sum.getHP_NO_PERCENT(),
                // 제논은 세 스탯의 %가 서로 다를 수 있어 따로 남긴다.
                pctOf("STR", sum), pctOf("DEX", sum), pctOf("LUK", sum))
                // calculateStat 의 세 성분을 스탯마다 그대로. 내림 지점을 오프라인에서 바꿔 볼 수 있게 한다.
                + String.format("\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%d\t%d",
                flatOf("STR", sum, level(documents)), noPctOf("STR", sum),
                flatOf("DEX", sum, level(documents)), noPctOf("DEX", sum),
                flatOf("LUK", sum, level(documents)), noPctOf("LUK", sum),
                sum.getALL_STAT(), sum.getALL_STAT_PERCENT(), sum.getALL_STAT_NO_PERCENT());

        // 소스별 STR+DEX+LUK 합. 어느 소스가 덜 잡히는지 그룹 대조용.
        StringBuilder bySource = new StringBuilder();
        for (StatSheet s : List.of(sheet.getAbilityPoint(), sheet.getSymbol(), sheet.getSkill(),
                sheet.getHexaStat(), sheet.getAbility(), sheet.getHyperStat(), sheet.getSetEffect(),
                sheet.getOtherStat(), sheet.getUnionArtifact(), sheet.getUnionChampion(),
                sheet.getUnionOccupied(), sheet.getUnionRaider())) {
            bySource.append('\t').append(s == null ? 0
                    : s.getSTR() + s.getDEX() + s.getLUK() + 3 * s.getALL_STAT()
                    + s.getSTR_NO_PERCENT() + s.getDEX_NO_PERCENT() + s.getLUK_NO_PERCENT()
                    + 3 * s.getALL_STAT_NO_PERCENT());
        }
        // 소스별 공격력(+%)도 같은 순서로.
        for (StatSheet s : List.of(sheet.getAbilityPoint(), sheet.getSymbol(), sheet.getSkill(),
                sheet.getHexaStat(), sheet.getAbility(), sheet.getHyperStat(), sheet.getSetEffect(),
                sheet.getOtherStat(), sheet.getUnionArtifact(), sheet.getUnionChampion(),
                sheet.getUnionOccupied(), sheet.getUnionRaider(), sheet.getConsumableItem())) {
            bySource.append('\t').append(s == null ? 0 : s.getATTACK_POWER());
        }
        int itemAttack = 0;
        int petAttack = 0;
        int cashAttack = 0;
        for (ItemSnapShot i : sheet.getItemEquip().values()) itemAttack += i.getStatSheet().getATTACK_POWER();
        for (ItemSnapShot i : sheet.getPetEquip().values()) petAttack += i.getStatSheet().getATTACK_POWER();
        for (ItemSnapShot i : sheet.getCashEquip().values()) cashAttack += i.getStatSheet().getATTACK_POWER();
        bySource.append('\t').append(itemAttack).append('\t').append(petAttack).append('\t').append(cashAttack);
        // 맨 끝에 붙인다. 앞 필드 번호가 밀리면 분석 스크립트가 통째로 어긋난다.
        bySource.append('\t').append(sheet.isInactiveCharacter());
        String sources = bySource.toString();

        System.out.printf("[TSV]\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%s\t%d\t%s\t%d\t%s\t%s\t%d\t%d"
                        + "\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%d"
                        + extra + sources + "%n",
                path.getFileName(), payload.path("job").asText(), payload.path("characterName").asText(),
                calc, api, bare,
                sheet.getExpiredArtifactCrystals(), sheet.isUnionRaiderDataMissing(),
                sheet.getExpiredCashItems(), sheet.isExpiredTitleOption(),
                sheet.getExpiredPetEquipments(), sheet.isLucidTransformSuspected(),
                sheet.isWeaponNormalizationFailed(), powerRaw, powerPct,
                flatOf(mainName, sum, level(documents)), pctOf(mainName, sum), noPctOf(mainName, sum),
                flatOf(subName, sum, level(documents)), pctOf(subName, sum), noPctOf(subName, sum),
                sum.getDAMAGE(), sum.getBOSS_DAMAGE(), sum.getCRITICAL_DAMAGE(), sum.getFINAL_DAMAGE(),
                subs.size());
    }

    /** 소스별로 공식의 각 항을 찍는다. 어느 소스에서 어긋나는지 좁힐 때 쓴다. */
    private void dumpTermsBySource(DataSheet sheet) {
        java.util.Map<String, StatSheet> sources = new java.util.LinkedHashMap<>();
        sources.put("abilityPoint", sheet.getAbilityPoint());
        sources.put("symbol", sheet.getSymbol());
        sources.put("skill", sheet.getSkill());
        sources.put("hexaStat", sheet.getHexaStat());
        sources.put("ability", sheet.getAbility());
        sources.put("hyperStat", sheet.getHyperStat());
        sources.put("setEffect", sheet.getSetEffect());
        sources.put("otherStat", sheet.getOtherStat());
        sources.put("unionArtifact", sheet.getUnionArtifact());
        sources.put("unionChampion", sheet.getUnionChampion());
        sources.put("unionOccupied", sheet.getUnionOccupied());
        sources.put("unionRaider", sheet.getUnionRaider());
        sources.put("conversionSF", sheet.getConversionStarforce());
        System.out.printf("%n  %-16s %8s %8s %8s %8s %8s%n", "소스", "공격력", "공%", "데미지", "보스뎀", "크뎀");
        for (Map.Entry<String, StatSheet> e : sources.entrySet()) {
            StatSheet v = e.getValue();
            if (v == null) continue;
            System.out.printf("  %-16s %8d %8d %8.2f %8.2f %8.2f%n", e.getKey(),
                    v.getATTACK_POWER(), v.getATTACK_POWER_PERCENT(),
                    v.getDAMAGE(), v.getBOSS_DAMAGE(), v.getCRITICAL_DAMAGE());
        }
        for (String label : List.of("장비", "펫", "캐시")) {
            Map<String, ItemSnapShot> map = label.equals("장비") ? sheet.getItemEquip()
                    : label.equals("펫") ? sheet.getPetEquip() : sheet.getCashEquip();
            int att = 0, pct = 0;
            double dmg = 0, boss = 0, crit = 0;
            for (ItemSnapShot i : map.values()) {
                StatSheet v = i.getStatSheet();
                att += v.getATTACK_POWER(); pct += v.getATTACK_POWER_PERCENT();
                dmg += v.getDAMAGE(); boss += v.getBOSS_DAMAGE(); crit += v.getCRITICAL_DAMAGE();
            }
            System.out.printf("  %-16s %8d %8d %8.2f %8.2f %8.2f%n", label, att, pct, dmg, boss, crit);
        }
    }

    /** 부위별로 우리가 뽑은 스탯과 원본 item_total_option 을 나란히 찍는다. */
    private void dumpItemDetail(DataSheet sheet, JsonNode itemEquip) {
        Map<String, JsonNode> raw = new java.util.LinkedHashMap<>();
        for (JsonNode item : itemEquip.path("item_equipment")) {
            raw.put(Jsons.text(item, "item_equipment_slot"), item);
        }
        System.out.printf("%n  %-16s %-24s %s%n", "부위", "우리(STR/DEX/LUK/ALL/공격력)", "원본 total(str/dex/luk/공격력)");
        for (Map.Entry<String, ItemSnapShot> e : sheet.getItemEquip().entrySet()) {
            String slot = e.getKey().contains(" - ") ? e.getKey().substring(e.getKey().lastIndexOf(" - ") + 3) : e.getKey();
            StatSheet s = e.getValue().getStatSheet();
            JsonNode r = raw.get(slot);
            JsonNode t = r == null ? null : r.path("item_total_option");
            System.out.printf("  %-16s %4d/%4d/%4d/%4d/%5d   %s%n", slot,
                    s.getSTR(), s.getDEX(), s.getLUK(), s.getALL_STAT(), s.getATTACK_POWER(),
                    t == null ? "-" : String.format("%s/%s/%s/%s  %s",
                            t.path("str").asText("0"), t.path("dex").asText("0"),
                            t.path("luk").asText("0"), t.path("attack_power").asText("0"),
                            Jsons.text(r, "item_name")));
        }
    }

    /** 유니온 항목을 골라 빼고 다시 계산한다. */
    private long unionVariant(CharacterSnapshot snapshot, Map<NexonEndpoint, JsonNode> documents,
                              boolean dropRaider, boolean dropOccupied,
                              boolean dropArtifact, boolean dropChampion) {
        DataSheet sheet = dataSheetService.getCurrentDataSheet(snapshot);
        if (dropRaider) sheet.setUnionRaider(new StatSheet("unionRaider"));
        if (dropOccupied) sheet.setUnionOccupied(new StatSheet("unionOccupied"));
        if (dropArtifact) sheet.setUnionArtifact(new StatSheet("unionArtifact"));
        if (dropChampion) sheet.setUnionChampion(new StatSheet("unionChampion"));
        sheet.setSumSheet(new StatSheet("종합"));
        sheet.buildSum();
        JsonNode basic = documents.get(NexonEndpoint.BASIC);
        return combatCalculationService.estimateCombatPower(
                sheet, basicParser.characterClass(basic), basicParser.characterLevel(basic));
    }

    /** 소스 하나를 비우고 다시 계산한다. */
    private long withoutSource(CharacterSnapshot snapshot, Map<NexonEndpoint, JsonNode> documents,
                               String source) {
        DataSheet sheet = dataSheetService.getCurrentDataSheet(snapshot);
        switch (source) {
            case "abilityPoint" -> sheet.setAbilityPoint(new StatSheet(source));
            case "symbol" -> sheet.setSymbol(new StatSheet(source));
            case "skill" -> sheet.setSkill(new StatSheet(source));
            case "hexaStat" -> sheet.setHexaStat(new StatSheet(source));
            case "ability" -> sheet.setAbility(new StatSheet(source));
            case "hyperStat" -> sheet.setHyperStat(new StatSheet(source));
            case "otherStat" -> sheet.setOtherStat(new StatSheet(source));
            case "setEffect" -> sheet.setSetEffect(new StatSheet(source));
            case "consumableItem" -> sheet.setConsumableItem(new StatSheet(source));
            case "conversionStarforce" -> sheet.setConversionStarforce(new StatSheet(source));
            default -> throw new IllegalArgumentException(source);
        }
        sheet.setSumSheet(new StatSheet("종합"));
        sheet.buildSum();
        JsonNode basic = documents.get(NexonEndpoint.BASIC);
        return combatCalculationService.estimateCombatPower(
                sheet, basicParser.characterClass(basic), basicParser.characterLevel(basic));
    }

    private long withoutUnion(CharacterSnapshot snapshot, Map<NexonEndpoint, JsonNode> documents) {
        DataSheet without = dataSheetService.getCurrentDataSheet(snapshot);
        without.setUnionOccupied(new StatSheet("unionOccupied"));
        without.setUnionRaider(new StatSheet("unionRaider"));
        without.setSumSheet(new StatSheet("종합"));
        without.buildSum();
        JsonNode basic = documents.get(NexonEndpoint.BASIC);
        return combatCalculationService.estimateCombatPower(
                without, basicParser.characterClass(basic), basicParser.characterLevel(basic));
    }

    /**
     * 제로 가설 조합 하나를 계산한다.
     *
     * @param weaponAttack   무기(라즐리) 공격력을 넣는가
     * @param astraBossDamage 아스트라 아워글라스 기본 옵션의 보스 데미지를 넣는가.
     *                        인벤 보고로는 이 45%가 표기 전투력에 안 들어간다고 한다.
     */
    private long zeroVariant(CharacterSnapshot snapshot, Map<NexonEndpoint, JsonNode> documents,
                             boolean weaponAttack, boolean astraBossDamage) {
        DataSheet sheet = dataSheetService.getCurrentDataSheet(snapshot);
        if (!weaponAttack) {
            ItemSnapShot weapon = sheet.getItemEquip().get("장비 - 무기");
            if (weapon != null) {
                weapon.getStatSheet().setATTACK_POWER(0);
            }
        }
        if (!astraBossDamage) {
            for (Map.Entry<String, ItemSnapShot> e : sheet.getItemEquip().entrySet()) {
                if (!e.getKey().contains("아스트라")) {
                    continue;
                }
                StatSheet s = e.getValue().getStatSheet();
                s.setBOSS_DAMAGE(s.getBOSS_DAMAGE() - astraBaseBossDamage(documents));
            }
        }
        sheet.buildSum();
        JsonNode basic = documents.get(NexonEndpoint.BASIC);
        return combatCalculationService.estimateCombatPower(
                sheet, basicParser.characterClass(basic), basicParser.characterLevel(basic));
    }

    /** 아스트라 아워글라스 기본 옵션의 보스 데미지. 표본에서는 45다. */
    private double astraBaseBossDamage(Map<NexonEndpoint, JsonNode> documents) {
        for (JsonNode item : documents.get(NexonEndpoint.ITEM_EQUIPMENT).path("item_equipment")) {
            if (Jsons.text(item, "item_name").contains("아스트라")) {
                return Jsons.parseDouble(Jsons.text(item.path("item_base_option"), "boss_damage"));
            }
        }
        return 0.0;
    }

    private int level(Map<NexonEndpoint, JsonNode> documents) {
        return basicParser.characterLevel(documents.get(NexonEndpoint.BASIC));
    }

    /** {@code CombatCalculationService.calculateStat}의 세 성분. */
    private double flatOf(String stat, StatSheet s, int level) {
        return switch (stat) {
            case "STR" -> s.getSTR() + (level / 9) * s.getSTR_PER_LEVEL9() + s.getALL_STAT();
            case "DEX" -> s.getDEX() + (level / 9) * s.getDEX_PER_LEVEL9() + s.getALL_STAT();
            case "INT" -> s.getINT() + (level / 9) * s.getINT_PER_LEVEL9() + s.getALL_STAT();
            case "LUK" -> s.getLUK() + (level / 9) * s.getLUK_PER_LEVEL9() + s.getALL_STAT();
            default -> 0.0;
        };
    }

    private double pctOf(String stat, StatSheet s) {
        int base = switch (stat) {
            case "STR" -> s.getSTR_PERCENT(); case "DEX" -> s.getDEX_PERCENT();
            case "INT" -> s.getINT_PERCENT(); case "LUK" -> s.getLUK_PERCENT(); default -> 0;
        };
        return base + s.getALL_STAT_PERCENT();
    }

    /** {@code CombatCalculationService.calculateStat}과 같은 식. */
    private double statValue(String stat, StatSheet s, int level) {
        return Math.floor(flatOf(stat, s, level) * (100.0 + pctOf(stat, s)) / 100.0 + noPctOf(stat, s));
    }

    private double noPctOf(String stat, StatSheet s) {
        int base = switch (stat) {
            case "STR" -> s.getSTR_NO_PERCENT(); case "DEX" -> s.getDEX_NO_PERCENT();
            case "INT" -> s.getINT_NO_PERCENT(); case "LUK" -> s.getLUK_NO_PERCENT(); default -> 0;
        };
        return base + s.getALL_STAT_NO_PERCENT();
    }
}
