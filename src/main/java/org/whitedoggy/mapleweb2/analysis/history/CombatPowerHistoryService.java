package org.whitedoggy.mapleweb2.analysis.history;

import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.dto.CharacterInfo;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.analysis.service.OcidService;
import org.whitedoggy.mapleweb2.analysis.service.SnapshotService;
import org.whitedoggy.mapleweb2.analysis.support.CacheTtlPolicy;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.hexa.HexaCoreParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 전투력 추이 조회. 일간 30개 / 월간 12개를 최신에서 과거로 훑는다.
 *
 * <p><b>캐릭터가 없는 시점은 미리 잘라낸다.</b> 생성 이전 날짜를 조회하면 API 가 200 을
 * 주면서 모든 필드를 null 로 채우는데, 그 응답만으로는 "전일치가 아직 안 열린 것"과
 * 구분되지 않는다(전일치는 다음날 02:00 KST 부터). 그래서 현재 스냅샷의
 * {@code character_date_create} 로 구간을 먼저 자른다. 그래도 빈 응답이 나오면
 * 그 지점에서 멈춘다.
 */
@Service
@RequiredArgsConstructor
public class CombatPowerHistoryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 넥슨 API 가 값을 주기 시작하는 첫 날. 이보다 이전은 전 필드 null 로 온다. */
    private static final LocalDate API_FIRST_DATE = LocalDate.of(2023, 12, 21);

    /** 동시에 진행할 날짜 수. flatMapSequential 이라 순서는 그대로다. */
    private static final int CONCURRENCY = 4;

    private final OcidService ocidService;
    private final SnapshotService snapshotService;
    private final DataSheetService dataSheetService;
    private final BasicParser basicParser;
    private final StatParser statParser;
    private final HexaCoreParser hexaCoreParser;
    private final GameData gameData;
    private final MapleCache cache;

    /** 한 번에 다 받는 형태. 차트만 그릴 때 쓴다. */
    public Mono<CombatPowerHistoryResponse> getHistory(String characterName, HistoryRange range) {
        return plan(characterName, range).flatMap(plan -> points(plan)
                .collectList()
                .map(points -> new CombatPowerHistoryResponse(
                        plan.ocid(),
                        range.name().toLowerCase(),
                        plan.characterInfo(),
                        range.count(),
                        points.size(),
                        plan.truncated(),
                        plan.truncatedFrom(),
                        points.stream()
                                .sorted(Comparator.comparing(CombatPowerHistoryPoint::date))
                                .toList()
                )));
    }

    /**
     * 진행 상황을 곁들여 흘려보내는 형태.
     *
     * <p>이벤트 순서: {@code meta} 한 번 → {@code point} 여러 번(최신부터) → {@code done}.
     * 도중에 실패하면 {@code error} 하나를 보내고 정상 종료한다 — 브라우저의
     * EventSource 는 스트림이 에러로 끊기면 자동 재연결을 시도하므로, 실패를
     * 이벤트로 알리고 닫는 편이 재조회 폭주를 막는다.
     */
    public Flux<ServerSentEvent<Object>> streamHistory(String characterName, HistoryRange range) {
        return plan(characterName, range)
                .flatMapMany(plan -> {
                    AtomicInteger index = new AtomicInteger();
                    int total = plan.dates().size();

                    ServerSentEvent<Object> meta = event("meta", new HistoryEvents.Meta(
                            plan.ocid(),
                            range.name().toLowerCase(),
                            plan.characterInfo(),
                            range.count(),
                            total,
                            plan.truncated(),
                            plan.truncatedFrom(),
                            plan.dates()
                    ));

                    Flux<ServerSentEvent<Object>> points = points(plan)
                            .map(point -> event("point",
                                    new HistoryEvents.Point(index.incrementAndGet(), total, point)));

                    Flux<ServerSentEvent<Object>> done = Flux.defer(() -> Flux.just(event("done",
                            new HistoryEvents.Done(index.get(), plan.truncated(), plan.truncatedFrom()))));

                    return Flux.concat(Flux.just(meta), points, done);
                })
                .onErrorResume(error -> Flux.just(event("error", errorEvent(error))));
    }

    /** 최신 → 과거 순으로 지점을 만든다. 빈 응답을 만나면 그 앞까지만 내보낸다. */
    private Flux<CombatPowerHistoryPoint> points(Plan plan) {
        return Flux.fromIterable(plan.dates())
                .flatMapSequential(date -> loadPoint(plan, date), CONCURRENCY)
                .takeWhile(Loaded::exists)
                .map(Loaded::point);
    }

    /**
     * 지점 하나. 지나간 날짜는 캐시를 먼저 본다 — 스냅샷 한 지점이 넥슨 호출 15회라,
     * 일간 30지점을 매번 받으면 450회다.
     *
     * <p>캐시에 두는 것은 43KB 짜리 {@code DataSheet} 이 아니라 숫자 네 개짜리
     * {@link CombatPowerHistoryPoint} 다. 차트가 필요한 건 그게 전부고, 구간을 눌렀을 때
     * 뜨는 상세는 {@code DataSheetService} 쪽 캐시가 따로 맡는다.
     */
    private Mono<Loaded> loadPoint(Plan plan, LocalDate date) {
        // 오늘은 date 파라미터를 붙이면 API 가 거절한다(OPENAPI00004). 계획을 세울 때
        // 이미 받아 둔 현재 스냅샷을 그대로 쓴다. 계속 변하는 값이라 캐시하지 않는다.
        if (date.equals(plan.today())) {
            CharacterSnapshot current = plan.current();
            return hexaMatrix(plan.ocid(), date, false)
                    .map(hexa -> toPoint(current, date, combatDataSheet(current), hexa));
        }

        String cacheKey = historyPointCacheKey(plan.ocid(), date);
        return cache.get(cacheKey, CombatPowerHistoryPoint.class)
                .map(point -> new Loaded(true, point))
                .switchIfEmpty(Mono.defer(() -> Mono.zip(
                                snapshotService.getSnapshotByOcid(plan.ocid(), date),
                                hexaMatrix(plan.ocid(), date, true))
                        .flatMap(both -> loadAndCachePoint(cacheKey, date, both.getT1(), both.getT2()))));
    }

    /**
     * 헥사 코어 문서. 15종 스냅샷에 넣지 않고 여기서만 부른다 — 조각이 필요한 것은
     * 추이 차트뿐이라, 스냅샷에 넣으면 monthly·yearly·검증까지 호출이 한 번씩 는다.
     * 캐시가 비었을 때만 부르므로 실제로 늘어나는 것은 콜드 조회 한 번이다.
     */
    private Mono<JsonNode> hexaMatrix(String ocid, LocalDate date, boolean includeDateParam) {
        return snapshotService.getDocument(NexonEndpoint.HEXA_MATRIX, ocid, date, includeDateParam);
    }

    /**
     * 빈 응답은 캐시하지 않는다. 아직 열리지 않은 시점일 수 있는데
     * ({@code takeWhile} 이 여기서 추이를 끊는다) 그걸 굳히면 영영 끊긴 채로 남는다.
     */
    private Mono<Loaded> loadAndCachePoint(
            String cacheKey, LocalDate date, CharacterSnapshot snapshot, JsonNode hexaMatrix) {
        DataSheet dataSheet = combatDataSheet(snapshot);
        Loaded loaded = toPoint(snapshot, date, dataSheet, hexaMatrix);
        if (!loaded.exists()) {
            return Mono.just(loaded);
        }
        Duration ttl = CacheTtlPolicy.forHistoryPoint(date, LocalDateTime.now(KST), dataSheet,
                loaded.point().solErdaFragments() != null);
        return cache.put(cacheKey, loaded.point(), ttl).thenReturn(loaded);
    }

    private Loaded toPoint(
            CharacterSnapshot snapshot, LocalDate date, DataSheet dataSheet, JsonNode hexaMatrix) {
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        if (basicParser.characterName(basic).isBlank()) {
            return Loaded.missing();
        }
        return new Loaded(true, new CombatPowerHistoryPoint(
                date,
                basicParser.characterLevel(basic),
                dataSheet == null ? null : dataSheet.getCombatPower(),
                apiCombatPower(snapshot),
                hexaCoreParser.solErdaFragments(hexaMatrix),
                hexaCoreParser.solErdaFragmentsRequired(hexaMatrix),
                expired(dataSheet)
        ));
    }

    /** 기간이 지나 계산에서 빠진 항목들. 시트를 못 만들었으면 알 수 없으니 null 이다. */
    private CombatPowerHistoryPoint.Expired expired(DataSheet dataSheet) {
        if (dataSheet == null) {
            return null;
        }
        return CombatPowerHistoryPoint.Expired.of(
                dataSheet.getExpiredArtifactCrystals(),
                dataSheet.getExpiredCashItems(),
                dataSheet.getExpiredPetEquipments(),
                dataSheet.isExpiredTitleOption());
    }

    /**
     * 우리가 다시 계산한 시트. 스냅샷 일부가 깨져 계산이 터지면 그 지점만 null 로 두고
     * 추이 전체를 잃지 않는다. null 이면 {@link CacheTtlPolicy} 가 짧은 TTL 을 준다.
     */
    private DataSheet combatDataSheet(CharacterSnapshot snapshot) {
        try {
            return dataSheetService.getCombatDataSheet(snapshot);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 계산 규칙이나 지점 모양을 바꾸면 접두사 버전을 올린다. 안 그러면 고친 값이 30일 동안
     * 안 보인다 ({@code maple:datasheet:v5} 와 같은 이유다).
     * v4 부터 조각 진행률의 분모가 들어 있다.
     */
    private static String historyPointCacheKey(String ocid, LocalDate date) {
        return "maple:history:v4:" + (ocid == null ? "" : ocid.trim()) + ":" + date;
    }

    private Long apiCombatPower(CharacterSnapshot snapshot) {
        String raw = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return (long) Math.floor(Jsons.parseDouble(raw));
    }

    /** ocid 와 현재 스냅샷을 받아 조회할 날짜를 확정한다. */
    private Mono<Plan> plan(String characterName, HistoryRange range) {
        LocalDate today = LocalDate.now(KST);
        return ocidService.getOcid(characterName)
                // 없는 이름이면 넥슨이 400 을 준다. 업스트림 URL 이 그대로 새어 나가지 않게 바꾼다.
                .onErrorMap(CombatPowerHistoryService::unknownCharacter,
                        error -> new CharacterNotFoundException(characterName))
                .flatMap(ocid -> snapshotService.getCurrentSnapshotByOcid(ocid, today)
                        .map(current -> buildPlan(ocid, range, today, current)));
    }

    private static boolean unknownCharacter(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode().value() == 400;
    }

    private Plan buildPlan(String ocid, HistoryRange range, LocalDate today, CharacterSnapshot current) {
        JsonNode basic = current.document(NexonEndpoint.BASIC);
        LocalDate created = basicParser.characterCreatedAt(basic);
        LocalDate floor = created == null || created.isBefore(API_FIRST_DATE) ? API_FIRST_DATE : created;

        List<LocalDate> all = range.dates(today);
        List<LocalDate> kept = all.stream().filter(date -> !date.isBefore(floor)).toList();
        boolean truncated = kept.size() < all.size();
        LocalDate truncatedFrom = truncated ? all.get(kept.size()) : null;

        return new Plan(ocid, today, current, characterInfo(basic), kept, truncated, truncatedFrom);
    }

    private CharacterInfo characterInfo(JsonNode basic) {
        return CharacterInfo.of(
                basicParser.characterName(basic),
                basicParser.characterClass(basic),
                basicParser.characterLevel(basic),
                basicParser.characterGuild(basic),
                basicParser.characterWorld(basic),
                basicParser.characterImage(basic),
                gameData
        );
    }

    private HistoryEvents.Error errorEvent(Throwable error) {
        String code = error instanceof CharacterNotFoundException ? "NOT_FOUND" : "ERROR";
        return new HistoryEvents.Error(code, message(error));
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private ServerSentEvent<Object> event(String name, Object payload) {
        return ServerSentEvent.builder().event(name).data(payload).build();
    }

    private record Plan(
            String ocid,
            LocalDate today,
            CharacterSnapshot current,
            CharacterInfo characterInfo,
            List<LocalDate> dates,
            boolean truncated,
            LocalDate truncatedFrom
    ) {
    }

    /** 지점 하나. {@code exists} 가 false 면 캐릭터가 없던 시점이라 여기서 끊는다. */
    private record Loaded(boolean exists, CombatPowerHistoryPoint point) {
        static Loaded missing() {
            return new Loaded(false, null);
        }
    }
}
