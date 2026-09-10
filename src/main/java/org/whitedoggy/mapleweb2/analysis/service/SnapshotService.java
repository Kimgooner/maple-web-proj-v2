package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SnapshotService {
    /**
     * 챔피언 명단은 계정 성질이라 자주 바뀌지 않는다. 추이 한 번이 서른 날짜를 부르므로,
     * 명단이 빈 날마다 현재 문서를 다시 부르지 않도록 ocid 로 짧게 들고 있는다.
     */
    private static final Duration CHAMPION_ROSTER_TTL = Duration.ofHours(6);

    private final NexonApiClient nexonApiClient;
    private final MapleCache cache;

    public Mono<CharacterSnapshot> getSnapshotByOcid(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, true);
    }

    public Mono<CharacterSnapshot> getCurrentSnapshotByOcid(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, false);
    }

    private Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date, boolean includeDateParam) {
        // 구독마다 새로 만든다. 어느 문서를 못 받았는지는 조립이 끝나야 알 수 있다.
        return Mono.defer(() -> fetchSnapshot(ocid, date, includeDateParam, ConcurrentHashMap.newKeySet()));
    }

    /**
     * 한 시점의 캐릭터를 이루는 문서들. 순서가 곧 {@code Mono.zip} 결과 배열의 순서다.
     *
     * <p>목록으로 두는 이유는 {@code Mono.zip} 의 인자가 여덟 개까지라, 튜플로 엮으면
     * 열여섯 번째부터는 중첩을 한 겹 더 쌓아야 하기 때문이다. 엔드포인트를 더할 때
     * 여기 한 줄만 넣으면 된다.
     */
    private static final List<NexonEndpoint> SNAPSHOT_ENDPOINTS = List.of(
            NexonEndpoint.BASIC,
            NexonEndpoint.STAT,
            NexonEndpoint.ITEM_EQUIPMENT,
            NexonEndpoint.CASH_ITEM_EQUIPMENT,
            NexonEndpoint.SET_EFFECT,
            NexonEndpoint.SYMBOL_EQUIPMENT,
            NexonEndpoint.PET_EQUIPMENT,
            NexonEndpoint.OTHER_STAT,
            NexonEndpoint.HYPER_STAT,
            NexonEndpoint.ABILITY,
            NexonEndpoint.SKILL_0,
            NexonEndpoint.SKILL_6,
            NexonEndpoint.HEXA_MATRIX_STAT,
            NexonEndpoint.HEXA_MATRIX,
            NexonEndpoint.UNION_RAIDER,
            NexonEndpoint.UNION_CHAMPION,
            NexonEndpoint.UNION_ARTIFACT);

    private Mono<CharacterSnapshot> fetchSnapshot(
            String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        List<Mono<JsonNode>> calls = SNAPSHOT_ENDPOINTS.stream()
                .map(endpoint -> fetchEndpoint(endpoint, ocid, date, includeDateParam, missing))
                .toList();
        return Mono.zip(calls, values -> {
            Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(NexonEndpoint.class);
            for (int index = 0; index < SNAPSHOT_ENDPOINTS.size(); index++) {
                documents.put(SNAPSHOT_ENDPOINTS.get(index), (JsonNode) values[index]);
            }
            return new CharacterSnapshot(ocid, date, documents, Set.copyOf(missing));
        }).flatMap(snapshot -> includeDateParam ? repairChampionRoster(snapshot) : Mono.just(snapshot));
    }

    /**
     * 그날만 비어 온 유니온 챔피언 명단을 현재 명단으로 채운다.
     *
     * <p>넥슨이 배지 총합은 그대로 주면서 명단만 빼고 주는 날이 있다. 배지는 챔피언으로
     * 등록된 캐릭터에게만 붙는데 명단이 없으면 그걸 가릴 수 없어, 챔피언이 아닌 캐릭터가
     * 하루치 배지를 받아 전투력이 솟았다가 다음 날 되돌아온다.
     *
     * <p>챔피언인지 아닌지는 계정 성질이라 날짜마다 뒤집히지 않는다. 그래서 빈 날에는
     * 날짜 없는 문서의 명단을 끌어와 채운다. <b>배지 총합은 그날 것을 그대로 둔다</b> —
     * 고치는 것은 "이 캐릭터가 챔피언인가" 하나뿐이다.
     *
     * <p>표본 700명 중 11명(1.6%)에서 걸렸고, 그 11명은 모두 다른 날짜에는 자기 명단에
     * 들어 있었다. 명단이 영영 비는 계정은 없었다. 현재 문서도 비어 있으면 판단할 근거가
     * 여전히 없으므로 손대지 않고 둔다 - 그때는 {@code ChampionParser} 의 기존 규칙을 따른다.
     */
    private Mono<CharacterSnapshot> repairChampionRoster(CharacterSnapshot snapshot) {
        JsonNode champion = snapshot.document(NexonEndpoint.UNION_CHAMPION);
        if (!rosterMissing(champion)) {
            return Mono.just(snapshot);
        }
        return currentRoster(snapshot.ocid())
                .filter(roster -> !roster.names().isEmpty())
                .map(roster -> withRoster(snapshot, champion, roster.names()))
                .defaultIfEmpty(snapshot);
    }

    /** 배지 총합은 왔는데 명단만 빈 상태. 둘 다 없으면 챔피언 자체를 안 쓰는 계정이라 손댈 것이 없다. */
    private boolean rosterMissing(JsonNode champion) {
        if (champion == null || champion.isNull()) {
            return false;
        }
        return champion.path("union_champion").isEmpty()
                && !champion.path("champion_badge_total_info").isEmpty();
    }

    private Mono<ChampionRoster> currentRoster(String ocid) {
        return cache.getOrLoad(
                championRosterCacheKey(ocid), ChampionRoster.class, CHAMPION_ROSTER_TTL,
                () -> nexonApiClient.get(NexonEndpoint.UNION_CHAMPION, ocid, null, false)
                        .retryWhen(retrySpec())
                        .map(SnapshotService::rosterOf)
                        .onErrorReturn(new ChampionRoster(List.of())));
    }

    private static ChampionRoster rosterOf(JsonNode champion) {
        List<String> names = new ArrayList<>();
        for (JsonNode entry : champion.path("union_champion")) {
            String name = entry.path("champion_name").asString("");
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return new ChampionRoster(List.copyOf(names));
    }

    /** 원본을 건드리지 않고 명단만 채운 문서로 갈아 끼운다. */
    private CharacterSnapshot withRoster(CharacterSnapshot snapshot, JsonNode champion, List<String> names) {
        ObjectNode repaired = (ObjectNode) champion.deepCopy();
        ArrayNode roster = JsonNodeFactory.instance.arrayNode();
        names.forEach(name -> roster.add(JsonNodeFactory.instance.objectNode().put("champion_name", name)));
        repaired.set("union_champion", roster);

        Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(snapshot.documents());
        documents.put(NexonEndpoint.UNION_CHAMPION, repaired);
        return new CharacterSnapshot(
                snapshot.ocid(), snapshot.date(), documents, snapshot.missingDocuments());
    }

    private String championRosterCacheKey(String ocid) {
        return "maple:championroster:v1:" + (ocid == null ? "" : ocid.trim());
    }

    /** 캐시에 담기는 현재 챔피언 명단. */
    public record ChampionRoster(List<String> names) {
    }

    private Mono<JsonNode> fetchEndpoint(NexonEndpoint endpoint, String ocid, LocalDate date,
                                         boolean includeDateParam, Set<NexonEndpoint> missing) {
        return nexonApiClient.get(endpoint, ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorResume(error -> missingDocument(endpoint, error, missing));
    }

    /**
     * 실패한 문서를 {@code NullNode} 로 채워 나머지 계산을 살린다.
     *
     * <p>다만 API 가 4xx 로 "그런 데이터는 없다"고 답한 것은 결손으로 세지 않는다.
     * 챌린저스 월드 캐릭터의 유니온 챔피언이 그렇고, 다시 물어도 같은 대답이 온다.
     * 이것까지 결손으로 세면 그 캐릭터는 캐시를 길게 둘 수 없다.
     */
    private Mono<JsonNode> missingDocument(NexonEndpoint endpoint, Throwable error, Set<NexonEndpoint> missing) {
        if (!isAbsent(error)) {
            missing.add(endpoint);
        }
        return Mono.just(nullNode());
    }

    /** 재시도가 소진돼 감싸인 예외는 여기 걸리지 않는다 — 그건 결손이 맞다. */
    private boolean isAbsent(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode().is4xxClientError()
                && response.getStatusCode().value() != 429;
    }

    private Retry retrySpec() {
        return Retry.backoff(2, Duration.ofMillis(300))
                .filter(this::isRetryable);
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientRequestException) {
            return true;
        }
        if (throwable instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError()
                    || responseException.getStatusCode().value() == 429;
        }
        return false;
    }

    private JsonNode nullNode() {
        return tools.jackson.databind.node.NullNode.getInstance();
    }

}
