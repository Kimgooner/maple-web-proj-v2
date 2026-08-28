package org.whitedoggy.mapleweb2.golden;

import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * {@code src/test/resources/fixtures}에 저장된 current 스냅샷을 읽어 {@link CharacterSnapshot}으로 만든다.
 *
 * <p>픽스처는 <b>기준일을 고정해</b>({@code date=2026-08-25}) 수집한 것이다. 과거 일자 데이터는
 * 불변이라 몇 번을 다시 받아도 같은 값이 나오고, 그래야 오차가 재현된다.
 * {@code date} 없이 받으면 조회 순간의 착용 프리셋 기준이라 보스↔사냥 전환에 따라 값이 움직인다.
 * 재수집은 {@code tools/collect-fixtures.py 2026-08-25} 로 한다.
 */
public final class FixtureLoader {

    private static final String DIR = "fixtures/";

    /** 픽스처 JSON의 문서 키 → 내부 엔드포인트 enum. */
    private static final Map<String, NexonEndpoint> DOCUMENT_KEYS = Map.ofEntries(
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

    private FixtureLoader() {
    }

    public record Fixture(String file, String job, String characterName, CharacterSnapshot snapshot) {
    }

    /**
     * 픽스처를 <b>한 건씩</b> 읽어 넘긴다.
     *
     * <p>스냅샷 하나가 파싱하면 수 MB다. 480건을 리스트에 모아 쥐면 힙이 버티지 못하므로,
     * 처리한 즉시 버릴 수 있도록 콜백으로 준다.
     */
    public static void forEach(ObjectMapper mapper, Consumer<Fixture> action) throws Exception {
        for (JsonNode entry : readJson(mapper, DIR + "index.json")) {
            String file = entry.path("file").asText();
            JsonNode payload = readGzipJson(mapper, DIR + file);
            action.accept(new Fixture(
                    file,
                    payload.path("job").asText(),
                    payload.path("characterName").asText(),
                    toSnapshot(payload)
            ));
        }
    }

    private static CharacterSnapshot toSnapshot(JsonNode payload) {
        Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(NexonEndpoint.class);
        JsonNode source = payload.path("documents");
        for (Map.Entry<String, NexonEndpoint> mapping : DOCUMENT_KEYS.entrySet()) {
            documents.put(mapping.getValue(), source.path(mapping.getKey()));
        }
        // 수집 시점. 아티팩트 만료 판정 기준일로 쓰이므로 실제 날짜여야 한다.
        String collectedAt = payload.path("collectedAt").asText("");
        LocalDate date = collectedAt.isBlank() ? LocalDate.EPOCH : LocalDate.parse(collectedAt);
        return new CharacterSnapshot(payload.path("ocid").asText(), date, documents);
    }

    private static JsonNode readJson(ObjectMapper mapper, String resource) throws Exception {
        try (InputStream in = open(resource)) {
            return mapper.readTree(in);
        }
    }

    private static JsonNode readGzipJson(ObjectMapper mapper, String resource) throws Exception {
        try (InputStream in = new GZIPInputStream(open(resource))) {
            return mapper.readTree(in);
        }
    }

    private static InputStream open(String resource) {
        InputStream in = FixtureLoader.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("픽스처를 찾을 수 없습니다: " + resource);
        }
        return in;
    }
}
