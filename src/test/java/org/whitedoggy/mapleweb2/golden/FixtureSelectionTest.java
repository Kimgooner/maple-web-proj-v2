package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * staging에 모아둔 후보 중 <b>스냅샷이 안정적이고 만료·미반영 플래그가 없는</b> 캐릭터를
 * 직업마다 N명 골라 {@code src/test/resources/fixtures}를 다시 만든다.
 *
 * <p>안정성이 필요한 이유: API의 stat 문서는 조회 순간의 캐릭터 상태를 그대로 찍는다.
 * 접속 직후처럼 유니온·버프가 아직 붙지 않은 순간이 저장되면 같은 스냅샷의 union·item
 * 문서와 어긋나, 계산이 맞아도 20% 넘게 벌어진다(2026-08-24 확인). 수집 시점과 이후 시점의
 * 전투력이 같은 캐릭터만 쓰면 그 사이 캐릭터가 움직이지 않았다는 뜻이라 스냅샷이 정합하다.
 *
 * <p>플래그 판정을 수집 스크립트에서 흉내 내지 않고 여기서 실제 계산 코드로 하는 이유는,
 * 판정이 갈리면 골든이 "플래그 없는 표본"이라는 전제를 잃기 때문이다.
 *
 * <pre>
 * ./gradlew test --tests '*FixtureSelectionTest' -Dselect.staging=&lt;경로&gt; [-Dselect.count=10]
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "select.staging", matches = ".+")
class FixtureSelectionTest {

    private static final Path FIXTURE_DIR = Path.of("src/test/resources/fixtures");

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
    @Autowired private StatParser statParser;
    @Autowired private ObjectMapper mapper;

    private record Candidate(String file, String job, String name, String world, int rank,
                             int flags, boolean stable, String reason) {
        /** 정렬 우선순위: 안정 → 무플래그 → 랭킹. */
        int penalty() {
            return (stable ? 0 : 1000) + flags;
        }
    }

    @Test
    void select() throws Exception {
        Path staging = Path.of(System.getProperty("select.staging"));
        int wanted = Integer.parseInt(System.getProperty("select.count", "10"));

        Map<String, Boolean> stability = readStability(staging);
        JsonNode index = mapper.readTree(Files.newInputStream(staging.resolve("index.json")));
        Map<String, List<Candidate>> byJob = new TreeMap<>();
        for (JsonNode entry : index) {
            Candidate candidate = evaluate(staging, entry, stability);
            byJob.computeIfAbsent(candidate.job(), key -> new ArrayList<>()).add(candidate);
        }

        StringBuilder report = new StringBuilder("직업별 후보 선별 (플래그 없는 캐릭터 우선)\n\n");
        List<Map<String, Object>> chosen = new ArrayList<>();
        int shortfall = 0;
        for (Map.Entry<String, List<Candidate>> entry : byJob.entrySet()) {
            List<Candidate> sorted = new ArrayList<>(entry.getValue());
            // 플래그 없는 것부터, 같으면 랭킹 순.
            sorted.sort(Comparator.comparingInt(Candidate::penalty).thenComparingInt(Candidate::rank));
            List<Candidate> take = sorted.stream().limit(wanted).toList();
            long clean = take.stream().filter(c -> c.penalty() == 0).count();
            if (take.size() < wanted || clean < wanted) {
                shortfall++;
                report.append(String.format("%-12s %d명 중 무플래그 %d명%n",
                        entry.getKey(), take.size(), clean));
                take.stream().filter(c -> c.penalty() > 0)
                        .forEach(c -> report.append("     └ ").append(c.name())
                                .append(" — ").append(c.stable() ? "" : "스냅샷 불안정 ")
                                .append(c.reason()).append('\n'));
            }
            for (Candidate candidate : take) {
                chosen.add(new LinkedHashMap<>(Map.of(
                        "file", candidate.file(), "job", candidate.job(),
                        "characterName", candidate.name(), "world", candidate.world(),
                        "rank", candidate.rank())));
            }
        }

        rewriteFixtures(staging, chosen);
        report.insert(0, String.format("직업 %d개 / 선정 %d명 / 부족한 직업 %d개%n"
                        + "기준: 수집 전후 전투력이 동일(스냅샷 정합) + 만료·미반영 플래그 없음%n%n",
                byJob.size(), chosen.size(), shortfall));
        Path out = Path.of("build/reports/golden/fixture-selection.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);
    }

    /** {@code stability.json}: 수집 시점과 이후 시점의 전투력이 같은지. 없으면 전부 안정으로 본다. */
    private Map<String, Boolean> readStability(Path staging) throws Exception {
        Path path = staging.getParent().resolve("stability.json");
        Map<String, Boolean> stability = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            System.out.println("[선별] stability.json 이 없어 안정성 검사를 건너뜁니다: " + path);
            return stability;
        }
        for (JsonNode entry : mapper.readTree(Files.newInputStream(path))) {
            stability.put(entry.path("file").asText(), entry.path("stable").asBoolean(false));
        }
        return stability;
    }

    private Candidate evaluate(Path staging, JsonNode entry, Map<String, Boolean> stability) throws Exception {
        String file = entry.path("file").asText();
        JsonNode payload;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(staging.resolve(file)))) {
            payload = mapper.readTree(in);
        }
        Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(NexonEndpoint.class);
        KEYS.forEach((key, endpoint) -> documents.put(endpoint, payload.path("documents").path(key)));

        int flags = 0;
        StringBuilder reason = new StringBuilder();
        try {
            DataSheet sheet = dataSheetService.getCurrentDataSheet(new CharacterSnapshot(
                    payload.path("ocid").asText(),
                    LocalDate.parse(payload.path("collectedAt").asText()),
                    documents));
            flags += add(reason, sheet.getExpiredArtifactCrystals(), "아티팩트 만료");
            flags += add(reason, sheet.getExpiredCashItems(), "캐시 만료");
            flags += add(reason, sheet.getExpiredPetEquipments(), "펫 장비 만료");
            flags += add(reason, sheet.isExpiredTitleOption() ? 1 : 0, "칭호 만료");
            flags += add(reason, sheet.isUnionRaiderDataMissing() ? 1 : 0, "유니온 미반영");
            flags += add(reason, sheet.isLucidTransformSuspected() ? 1 : 0, "루시드 변신 의심");
            // 표본은 모두 랭킹 상위(레벨 260 이상)라 전투력이 최소 수천만이다.
            // 100만 미만은 존재할 수 없는 값이므로 API 응답이 깨진 것으로 본다.
            String apiText = statParser.currentCombatPower(documents.get(NexonEndpoint.STAT));
            long api = apiText == null ? 0L : (long) Math.floor(Jsons.parseDouble(apiText));
            flags += add(reason, api > 0 && api < 1_000_000 ? 1 : 0, "API 전투력 이상치");
        } catch (RuntimeException e) {
            // 계산이 터지는 후보는 표본에서 밀어낸다. 원인은 골든에서 따로 본다.
            flags += 100;
            reason.append("계산 실패: ").append(e.getClass().getSimpleName());
        }
        return new Candidate(file, entry.path("job").asText(), entry.path("characterName").asText(),
                entry.path("world").asText(), entry.path("rank").asInt(), flags,
                stability.getOrDefault(file, true), reason.toString());
    }

    private int add(StringBuilder reason, int count, String label) {
        if (count > 0) {
            if (!reason.isEmpty()) {
                reason.append(", ");
            }
            reason.append(label).append(' ').append(count);
        }
        return count;
    }

    private void rewriteFixtures(Path staging, List<Map<String, Object>> chosen) throws Exception {
        try (var files = Files.list(FIXTURE_DIR)) {
            for (Path path : files.toList()) {
                if (path.getFileName().toString().endsWith(".json.gz")) {
                    Files.delete(path);
                }
            }
        }
        for (Map<String, Object> entry : chosen) {
            String file = (String) entry.get("file");
            Files.copy(staging.resolve(file), FIXTURE_DIR.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(FIXTURE_DIR.resolve("index.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(chosen));
    }
}
