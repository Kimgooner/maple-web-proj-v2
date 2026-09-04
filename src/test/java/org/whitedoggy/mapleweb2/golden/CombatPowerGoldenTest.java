package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.service.CombatCalculationService;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 리팩터링 회귀 방지용 골든 테스트.
 *
 * <p>직업당 2명(제로·제논·데몬어벤져는 5명)의 current 스냅샷을 고정해 두고,
 * <b>현재 착용 프리셋</b> 기준 계산 결과를 기대값과 비교한다. 넥슨 API가 주는 전투력이
 * 조회 시점 착용 프리셋 기준이므로 비교 대상도 current여야 한다.
 *
 * <p>기대값 파일이 없거나 계산 규칙을 의도적으로 바꾼 경우 갱신한다:
 * <pre>./gradlew test --tests '*CombatPowerGoldenTest' -Dgolden.update=true</pre>
 * 갱신 후에는 반드시 diff를 확인하고, 값이 왜 바뀌었는지 커밋 메시지에 남긴다.
 */
@SpringBootTest
class CombatPowerGoldenTest {

    private static final Path EXPECTED_PATH = Path.of("src/test/resources/fixtures/expected.json");
    private static final Path REPORT_PATH = Path.of("build/reports/golden/combat-power.txt");

    /** 계산이 아직 미완이라 값 자체를 신뢰하지 않는 직업. 골든은 기록하되 리포트에서 표시한다. */
    private static final List<String> UNSUPPORTED_JOBS = List.of("제로", "제논", "데몬어벤져");

    @Autowired
    private DataSheetService dataSheetService;

    @Autowired
    private StatParser statParser;

    @Autowired
    private CombatCalculationService combatCalculationService;

    @Autowired
    private BasicParser basicParser;

    @Autowired
    private GameData gameData;

    @Autowired
    private ObjectMapper mapper;

    private record Result(
            String file,
            String job,
            String characterName,
            PresetSelection preset,
            Long combatPower,
            Long apiCombatPower,
            boolean lucidTransformSuspected,
            int expiredArtifactCrystals,
            boolean unionRaiderDataMissing,
            boolean unionMissingFromApiValue,
            boolean belowSupportedLevel,
            int expiredCashItems,
            boolean expiredTitleOption,
            int expiredPetEquipments,
            boolean weaponNormalizationFailed,
            boolean weaponMissing,
            boolean unknownWeapon,
            boolean inactiveCharacter,
            boolean consumableItemMismatch,
            boolean pirateBlessApplied,
            String error
    ) {
        double errorRatePercent() {
            if (combatPower == null || apiCombatPower == null || apiCombatPower == 0L) {
                return Double.NaN;
            }
            return Math.abs(combatPower - apiCombatPower) * 100.0 / apiCombatPower;
        }
    }

    @Test
    void currentPresetCombatPowerMatchesGolden() throws Exception {
        List<Result> results = new ArrayList<>();
        FixtureLoader.forEach(mapper, fixture -> results.add(evaluate(fixture)));
        results.sort(Comparator.comparing(Result::file));

        writeReport(results);

        if (Boolean.getBoolean("golden.update")) {
            writeExpected(results);
            System.out.println("[golden] 기대값을 갱신했습니다: " + EXPECTED_PATH.toAbsolutePath());
            System.out.println("[golden] git diff 로 변화를 확인하고 커밋하세요.");
            return;
        }

        JsonNode expected = readExpected();
        if (expected == null) {
            fail("기대값 파일이 없습니다. 먼저 -Dgolden.update=true 로 한 번 실행해 기준선을 기록하세요: "
                    + EXPECTED_PATH);
        }

        List<String> mismatches = new ArrayList<>();
        JsonNode entries = expected.path("entries");
        for (Result result : results) {
            if (result.error() != null) {
                mismatches.add(result.file() + " (" + result.job() + "): 계산 실패 - " + result.error());
                continue;
            }
            JsonNode want = entries.path(result.file());
            if (want.isMissingNode() || want.isNull()) {
                mismatches.add(result.file() + ": 기대값에 없는 픽스처");
                continue;
            }
            compare(mismatches, result, want);
        }

        if (entries.size() != results.size()) {
            mismatches.add("픽스처 수 불일치: 기대값 " + entries.size() + "건 / 실제 " + results.size() + "건");
        }

        if (!mismatches.isEmpty()) {
            fail("골든 불일치 " + mismatches.size() + "건. 리포트: "
                    + REPORT_PATH.toAbsolutePath() + "\n  - "
                    + String.join("\n  - ", mismatches));
        }
    }

    private void compare(List<String> mismatches, Result result, JsonNode want) {
        String prefix = result.file() + " (" + result.job() + "/" + result.characterName() + ") ";
        if (!want.path("combatPower").asText("").equals(String.valueOf(result.combatPower()))) {
            mismatches.add(prefix + "전투력: 기대 " + want.path("combatPower").asText()
                    + " → 실제 " + result.combatPower());
        }
        checkInt(mismatches, prefix, "itemPreset", want, result.preset().itemPreset());
        checkInt(mismatches, prefix, "abilityPreset", want, result.preset().abilityPreset());
        checkInt(mismatches, prefix, "hyperStatPreset", want, result.preset().hyperStatPreset());
        checkInt(mismatches, prefix, "unionRaiderPreset", want, result.preset().unionRaiderPreset());
        checkInt(mismatches, prefix, "expiredArtifactCrystals", want, result.expiredArtifactCrystals());
        checkInt(mismatches, prefix, "expiredCashItems", want, result.expiredCashItems());
        checkInt(mismatches, prefix, "expiredPetEquipments", want, result.expiredPetEquipments());
        String wantWeapon = want.path("weaponNormalizationFailed").asText("false");
        if (!wantWeapon.equals(String.valueOf(result.weaponNormalizationFailed()))) {
            mismatches.add(prefix + "weaponNormalizationFailed: 기대 " + wantWeapon
                    + " → 실제 " + result.weaponNormalizationFailed());
        }
        String wantTitle = want.path("expiredTitleOption").asText("false");
        if (!wantTitle.equals(String.valueOf(result.expiredTitleOption()))) {
            mismatches.add(prefix + "expiredTitleOption: 기대 " + wantTitle
                    + " → 실제 " + result.expiredTitleOption());
        }
        String wantUnion = want.path("unionRaiderDataMissing").asText("false");
        if (!wantUnion.equals(String.valueOf(result.unionRaiderDataMissing()))) {
            mismatches.add(prefix + "unionRaiderDataMissing: 기대 " + wantUnion
                    + " → 실제 " + result.unionRaiderDataMissing());
        }
        String wantConsumable = want.path("consumableItemMismatch").asText("false");
        if (!wantConsumable.equals(String.valueOf(result.consumableItemMismatch()))) {
            mismatches.add(prefix + "consumableItemMismatch: 기대 " + wantConsumable
                    + " → 실제 " + result.consumableItemMismatch());
        }
        String wantPirate = want.path("pirateBlessApplied").asText("false");
        if (!wantPirate.equals(String.valueOf(result.pirateBlessApplied()))) {
            mismatches.add(prefix + "pirateBlessApplied: 기대 " + wantPirate
                    + " → 실제 " + result.pirateBlessApplied());
        }
        String wantLucid = want.path("lucidTransformSuspected").asText("false");
        String actualLucid = String.valueOf(result.lucidTransformSuspected());
        if (!wantLucid.equals(actualLucid)) {
            mismatches.add(prefix + "lucidTransformSuspected: 기대 " + wantLucid
                    + " → 실제 " + actualLucid);
        }
    }

    /**
     * 만료·미반영 항목은 우리가 빼지만 API 전투력은 아직 포함하고 있다.
     * 이때 오차는 계산 정확도가 아니라 API 데이터의 지연을 재는 값이라 평균에서 제외한다.
     */
    /**
     * API가 준 전투력 자체가 깨진 경우.
     *
     * <p>표본은 모두 랭킹 상위(레벨 260 이상)라 전투력이 최소 수천만이다. 100만 미만은
     * 계산 오차로 설명할 수 없는 값이므로 API 응답 이상으로 보고 평균에서 뺀다.
     * 데이터에 맞춘 기준이 아니라, 그 구간의 값이 애초에 존재할 수 없다는 뜻이다.
     */
    private boolean hasImplausibleApiValue(Result result) {
        return result.apiCombatPower() > 0 && result.apiCombatPower() < 1_000_000;
    }

    private boolean hasStaleApiData(Result result) {
        return result.expiredArtifactCrystals() > 0
                || result.unionRaiderDataMissing()
                || result.unionMissingFromApiValue()
                || result.belowSupportedLevel()
                || result.expiredCashItems() > 0
                || result.expiredTitleOption()
                || result.expiredPetEquipments() > 0
                || result.inactiveCharacter();
    }

    /**
     * 소모 아이템 직업인데 API 전투력과 값이 다른 경우.
     *
     * <p>화살·표창·총알은 API가 주지 않아 {@code consumable-attack}의 대표값을 더한다.
     * 그 사람이 실제로 무엇을 끼고 있었는지는 알 수 없으므로 대표값과 다르면 그만큼 어긋나고,
     * 이 차이는 우리 계산의 오류가 아니라 관측 불가에서 오는 것이다. 값이 정확히 맞는
     * 사람은 대표값이 맞았다는 뜻이라 플래그를 세우지 않는다.
     */
    private boolean consumableItemMismatch(String job, Long combatPower, Long apiCombatPower) {
        if (combatPower == null || apiCombatPower == null || apiCombatPower == 0L) {
            return false;
        }
        return gameData.usesConsumableItem(job) && !combatPower.equals(apiCombatPower);
    }

    /** 무기 추가옵션 단계를 못 찾은 경우. 우리 표의 결손이므로 오차가 아니라 버그로 따로 센다. */
    private boolean hasWeaponNormalizationFailure(Result result) {
        return result.weaponNormalizationFailed();
    }

    private void checkInt(List<String> mismatches, String prefix, String field, JsonNode want, int actual) {
        int expectedValue = want.path(field).asInt(Integer.MIN_VALUE);
        if (expectedValue != actual) {
            mismatches.add(prefix + field + ": 기대 " + expectedValue + " → 실제 " + actual);
        }
    }

    private Result evaluate(FixtureLoader.Fixture fixture) {
        CharacterSnapshot snapshot = fixture.snapshot();
        Long apiCombatPower = apiCombatPower(snapshot);
        try {
            PresetSelection preset = dataSheetService.getCurrentPresetSelection(snapshot);
            DataSheet sheet = dataSheetService.getCurrentDataSheet(snapshot);
            return new Result(
                    fixture.file(),
                    fixture.job(),
                    fixture.characterName(),
                    preset,
                    sheet.getCombatPower(),
                    apiCombatPower,
                    sheet.isLucidTransformSuspected(),
                    sheet.getExpiredArtifactCrystals(),
                    sheet.isUnionRaiderDataMissing(),
                    unionMissingFromApiValue(snapshot, sheet.getCombatPower(), apiCombatPower),
                    !gameData.isSupportedLevel(
                            basicParser.characterLevel(snapshot.document(NexonEndpoint.BASIC))),
                    sheet.getExpiredCashItems(),
                    sheet.isExpiredTitleOption(),
                    sheet.getExpiredPetEquipments(),
                    sheet.isWeaponNormalizationFailed(),
                    sheet.isWeaponMissing(),
                    sheet.isUnknownWeapon(),
                    sheet.isInactiveCharacter(),
                    consumableItemMismatch(fixture.job(), sheet.getCombatPower(), apiCombatPower),
                    sheet.isPirateBlessApplied(),
                    null
            );
        } catch (Exception exception) {
            return new Result(
                    fixture.file(),
                    fixture.job(),
                    fixture.characterName(),
                    new PresetSelection(-1, -1, -1, -1),
                    null,
                    apiCombatPower,
                    false,
                    0,
                    false,
                    false,
                    false,
                    0,
                    false,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    /**
     * 넥슨이 준 전투력 자체가 유니온을 빼고 집계된 경우.
     *
     * <p>공격대원·점령 효과를 빼고 다시 계산한 값이 API 전투력과 <b>정수까지</b> 같으면,
     * 우리 계산이 틀린 것이 아니라 그날 넥슨 쪽 {@code stat} 집계에서 유니온이 빠진 것이다.
     * 20~29% 과대로 나타난다.
     *
     * <p>{@code unionRaiderDataMissing}과는 다르다. 그쪽은 응답에 공격대 데이터가 아예
     * 없는 경우고, 이쪽은 데이터는 멀쩡한데 전투력에만 안 들어간 경우다.
     *
     * <p>계산값이 이미 API와 같으면 세우지 않는다. 유니온 기여가 0인 캐릭터(챌린저스 월드
     * 등)는 빼도 값이 그대로라 조건에 걸리는데, 그건 결함이 아니라 맞은 것이기 때문이다.
     */
    private boolean unionMissingFromApiValue(
            CharacterSnapshot snapshot, Long combatPower, Long apiCombatPower) {
        if (combatPower == null || apiCombatPower == null || apiCombatPower == 0L
                || combatPower.equals(apiCombatPower)) {
            return false;
        }
        DataSheet without = dataSheetService.getCurrentDataSheet(snapshot);
        without.setUnionOccupied(new StatSheet("unionOccupied"));
        without.setUnionRaider(new StatSheet("unionRaider"));
        without.setSumSheet(new StatSheet("종합"));
        without.buildSum();
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        long bare = combatCalculationService.estimateCombatPower(
                without, basicParser.characterClass(basic), basicParser.characterLevel(basic));
        return bare == apiCombatPower;
    }

    private Long apiCombatPower(CharacterSnapshot snapshot) {
        String raw = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return (long) Math.floor(Jsons.parseDouble(raw));
    }

    private void writeExpected(List<Result> results) throws Exception {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Result result : results) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("job", result.job());
            entry.put("characterName", result.characterName());
            entry.put("itemPreset", result.preset().itemPreset());
            entry.put("abilityPreset", result.preset().abilityPreset());
            entry.put("hyperStatPreset", result.preset().hyperStatPreset());
            entry.put("unionRaiderPreset", result.preset().unionRaiderPreset());
            entry.put("combatPower", result.combatPower());
            entry.put("apiCombatPower", result.apiCombatPower());
            entry.put("errorRatePercent", round2(result.errorRatePercent()));
            entry.put("lucidTransformSuspected", result.lucidTransformSuspected());
            entry.put("expiredArtifactCrystals", result.expiredArtifactCrystals());
            entry.put("unionRaiderDataMissing", result.unionRaiderDataMissing());
            entry.put("expiredCashItems", result.expiredCashItems());
            entry.put("expiredTitleOption", result.expiredTitleOption());
            entry.put("expiredPetEquipments", result.expiredPetEquipments());
            entry.put("weaponNormalizationFailed", result.weaponNormalizationFailed());
            entry.put("consumableItemMismatch", result.consumableItemMismatch());
            entry.put("pirateBlessApplied", result.pirateBlessApplied());
            if (result.error() != null) {
                entry.put("error", result.error());
            }
            entries.put(result.file(), entry);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("note", "current(착용 프리셋) 기준 계산 결과. -Dgolden.update=true 로만 갱신할 것.");
        root.put("entries", entries);

        Files.createDirectories(EXPECTED_PATH.getParent());
        Files.writeString(EXPECTED_PATH, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private JsonNode readExpected() throws Exception {
        if (!Files.exists(EXPECTED_PATH)) {
            return null;
        }
        return mapper.readTree(Files.readString(EXPECTED_PATH));
    }

    private void writeReport(List<Result> results) throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("current 프리셋 기준 계산값 vs API 전투력\n");
        report.append("픽스처 ").append(results.size()).append("건\n\n");

        Map<String, List<Double>> byJob = new TreeMap<>();
        List<Result> staleApi = new ArrayList<>();
        List<Result> implausible = new ArrayList<>();
        List<Result> consumableItem = new ArrayList<>();
        int failed = 0;
        for (Result result : results) {
            if (result.error() != null) {
                failed++;
                continue;
            }
            if (hasImplausibleApiValue(result)) {
                implausible.add(result);
                continue;
            }
            if (hasStaleApiData(result)) {
                // API 쪽 데이터가 최신이 아닌 경우다. 만료된 아티팩트를 우리는 뺐는데 API 전투력은
                // 아직 포함하고 있거나, 유니온 개편 이후 미접속으로 공격대 데이터가 비어 있다.
                // 이때 오차는 우리 정확도가 아니라 API의 지연을 재는 값이라 평균에서 제외한다.
                staleApi.add(result);
                continue;
            }
            if (result.consumableItemMismatch()) {
                // 소모 아이템은 API가 주지 않아 대표값을 더한다. 그 사람이 실제로 낀 것과 다르면
                // 그만큼 어긋나는데, 이는 계산 오류가 아니라 관측 불가라 평균에서 제외한다.
                consumableItem.add(result);
                continue;
            }
            double rate = result.errorRatePercent();
            if (!Double.isNaN(rate)) {
                byJob.computeIfAbsent(result.job(), key -> new ArrayList<>()).add(rate);
            }
        }

        report.append(String.format("%-18s %8s %8s %8s%n", "직업", "평균오차%", "최대오차%", "표본"));
        List<Map.Entry<String, List<Double>>> rows = new ArrayList<>(byJob.entrySet());
        rows.sort(Comparator.comparingDouble((Map.Entry<String, List<Double>> e) -> average(e.getValue())).reversed());
        for (Map.Entry<String, List<Double>> row : rows) {
            String mark = UNSUPPORTED_JOBS.contains(row.getKey()) ? "  (계산 미완)" : "";
            report.append(String.format("%-18s %8.2f %8.2f %8d%s%n",
                    row.getKey(), average(row.getValue()), max(row.getValue()), row.getValue().size(), mark));
        }

        report.append("\nAPI 전투력 이상치(응답 자체가 깨짐, 평균에서 뺌): ")
                .append(implausible.size()).append("건\n");
        for (Result result : implausible) {
            report.append(String.format("   %-10s %-11s API %,d / 계산 %,d%n",
                    result.job(), result.characterName(),
                    result.apiCombatPower(), result.combatPower()));
        }

        report.append("\nAPI 데이터 미반영으로 평균에서 제외: ")
                .append(staleApi.size()).append("건\n");
        for (Result result : staleApi) {
            List<String> reasons = new ArrayList<>();
            if (result.expiredArtifactCrystals() > 0) {
                reasons.add("아티팩트 만료 " + result.expiredArtifactCrystals() + "개");
            }
            if (result.unionRaiderDataMissing()) {
                reasons.add("유니온 공격대 미반영");
            }
            if (result.unionMissingFromApiValue()) {
                reasons.add("API 전투력이 유니온을 빼고 집계됨");
            }
            if (result.belowSupportedLevel()) {
                reasons.add("지원 최소 레벨 미만");
            }
            if (result.expiredCashItems() > 0) {
                reasons.add("캐시 만료 " + result.expiredCashItems() + "개");
            }
            if (result.expiredTitleOption()) {
                reasons.add("칭호 옵션 만료");
            }
            if (result.expiredPetEquipments() > 0) {
                reasons.add("펫 장비 만료 " + result.expiredPetEquipments() + "개");
            }
            if (result.inactiveCharacter()) {
                reasons.add("최근 7일 미접속");
            }
            if (result.weaponMissing()) {
                reasons.add("무기 미착용");
            } else if (result.unknownWeapon()) {
                reasons.add("등록 안 된 무기");
            } else if (result.weaponNormalizationFailed()) {
                reasons.add("무기 정규화 실패");
            }
            report.append(String.format("   %-10s %-11s 오차 %6.2f%%  %s%n",
                    result.job(), result.characterName(), result.errorRatePercent(),
                    String.join(", ", reasons)));
        }

        report.append("\n소모 아이템 직업(화살·표창·총알, API 미관측)으로 평균에서 제외: ")
                .append(consumableItem.size()).append("건\n");
        for (Result result : consumableItem) {
            report.append(String.format("   %-10s %-11s 오차 %6.3f%%  소모품 대표값 공격력 %d%n",
                    result.job(), result.characterName(), result.errorRatePercent(),
                    gameData.consumableAttackOf(result.job())));
        }

        report.append("\n파이렛 블레스 적용(모험가 해적, 장비 힘↔민첩 교환): ")
                .append(results.stream().filter(Result::pirateBlessApplied).count()).append("건\n");
        for (Result result : results) {
            if (result.pirateBlessApplied()) {
                report.append(String.format("   %-10s %-11s 오차 %6.3f%%%n",
                        result.job(), result.characterName(), result.errorRatePercent()));
            }
        }

        report.append("\n계산 실패: ").append(failed).append("건\n");
        for (Result result : results) {
            if (result.error() != null) {
                report.append("  ").append(result.file()).append(" (").append(result.job())
                        .append(") ").append(result.error()).append('\n');
            }
        }

        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report.toString());
        System.out.println(report);
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static double max(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    private static Double round2(double value) {
        if (Double.isNaN(value)) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
