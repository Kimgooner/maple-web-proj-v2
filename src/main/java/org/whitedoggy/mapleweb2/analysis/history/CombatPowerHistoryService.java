package org.whitedoggy.mapleweb2.analysis.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.analysis.dto.CharacterInfo;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentPresetInfo;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class CombatPowerHistoryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 넥슨 API 가 값을 주기 시작하는 첫 날. 이보다 이전은 전 필드 null 로 온다. */
    /** 오늘치 앞머리를 들고 있는 시간. 넥슨 반영 지연(평균 15분)보다 짧게 잡는다. */
    private static final Duration TODAY_HEAD_TTL = Duration.ofMinutes(5);

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
    private final HistoryTraffic traffic;

    /** 한 번에 다 받는 형태. 차트만 그릴 때 쓴다. */
    public Mono<CombatPowerHistoryResponse> getHistory(String characterName, HistoryRange range) {
        // 스트림과 무게가 같으므로 같이 센다 - 이쪽으로 들어온 사람도 남을 기다리게 한다.
        return Mono.defer(() -> {
                    traffic.enter();
                    return loadHistory(characterName, range);
                })
                .doFinally(signal -> traffic.leave());
    }

    private Mono<CombatPowerHistoryResponse> loadHistory(String characterName, HistoryRange range) {
        return plan(characterName, range).flatMap(plan -> points(plan)
                .collectList()
                .map(points -> new CombatPowerHistoryResponse(
                        plan.ocid(),
                        range.name().toLowerCase(),
                        plan.characterInfo(),
                        plan.preset(),
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
        return Flux.defer(() -> {
                    traffic.enter();
                    return streamBody(characterName, range);
                })
                .doFinally(signal -> traffic.leave());
    }

    private Flux<ServerSentEvent<Object>> streamBody(String characterName, HistoryRange range) {
        return plan(characterName, range)
                .flatMapMany(plan -> {
                    AtomicInteger index = new AtomicInteger();
                    int total = plan.dates().size();

                    ServerSentEvent<Object> meta = event("meta", new HistoryEvents.Meta(
                            plan.ocid(),
                            range.name().toLowerCase(),
                            plan.characterInfo(),
                            plan.preset(),
                            range.count(),
                            total,
                            plan.truncated(),
                            plan.truncatedFrom(),
                            plan.dates(),
                            traffic.current()
                    ));

                    Flux<ServerSentEvent<Object>> points = points(plan)
                            .map(point -> event("point", new HistoryEvents.Point(
                                    index.incrementAndGet(), total, point, traffic.current())));

                    Flux<ServerSentEvent<Object>> done = Flux.defer(() -> Flux.just(event("done",
                            new HistoryEvents.Done(index.get(), plan.truncated(), plan.truncatedFrom()))));

                    return Flux.concat(Flux.just(meta), points, done);
                })
                .onErrorResume(error -> Flux.just(event("error", errorEvent(error))));
    }

    /**
     * 장비 프리셋이 갈린 날짜를 다른 날짜 기준으로 되돌려 다시 계산한다.
     *
     * <p>보스 프리셋을 고르는 점수가 같아 갈리는 날이 있다. 장비 구성이 거의 같은 두 프리셋을
     * 두고 있으면 하루만 다른 번호가 뽑히고, 그날 전투력이 뚝 떨어진 것처럼 보인다. 캐릭터는
     * 아무것도 안 했는데 그래프에 골짜기가 생긴다.
     *
     * <p>고치는 방법은 단순하다. <b>이 구간에서 제일 많이 쓴 번호를 정답으로 보고</b>, 그와
     * 다른 번호가 뽑힌 날만 그 번호로 다시 계산한다. 나머지 날은 캐시 그대로라 손대지 않는다.
     *
     * <p>되돌린 값은 따로 캐시한다 - 원래 계산과 섞이면 어느 쪽을 보고 있는지 알 수 없다.
     */
    public Mono<CombatPowerHistoryResponse> repairHistory(String characterName, HistoryRange range) {
        return getHistory(characterName, range).flatMap(original -> {
            Integer target = dominantItemPreset(original.points());
            if (target == null) {
                return Mono.just(original);
            }
            List<CombatPowerHistoryPoint> odd = original.points().stream()
                    .filter(point -> point.itemPreset() != null && !point.itemPreset().equals(target))
                    .toList();
            if (odd.isEmpty()) {
                return Mono.just(original);
            }
            return Flux.fromIterable(odd)
                    .flatMapSequential(point -> repairPoint(original.ocid(), point.date(), target), CONCURRENCY)
                    .collectList()
                    .map(fixed -> withRepaired(original, fixed));
        });
    }

    /** 이 구간에서 제일 많이 쓴 장비 프리셋 번호. 다 비어 있으면 null. */
    private Integer dominantItemPreset(List<CombatPowerHistoryPoint> points) {
        Map<Integer, Long> counts = points.stream()
                .map(CombatPowerHistoryPoint::itemPreset)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(p -> p, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Mono<CombatPowerHistoryPoint> repairPoint(String ocid, LocalDate date, int itemPreset) {
        LocalDate today = LocalDate.now(KST);
        // 오늘은 date 를 붙이면 API 가 거절한다(OPENAPI00004). 계속 변하는 값이라 캐시도 안 한다.
        if (date.equals(today)) {
            return snapshotService.getCurrentSnapshotByOcid(ocid, today)
                    .map(snapshot -> forcedPoint(snapshot, date, itemPreset));
        }
        String cacheKey = historyPointCacheKey(ocid, date) + ":p" + itemPreset;
        return cache.get(cacheKey, CombatPowerHistoryPoint.class)
                .switchIfEmpty(Mono.defer(() -> snapshotService.getSnapshotByOcid(ocid, date)
                        .map(snapshot -> forcedPoint(snapshot, date, itemPreset))
                        .flatMap(point -> cache.put(cacheKey, point,
                                CacheTtlPolicy.forHistoryPoint(date, LocalDateTime.now(KST), null, true)))));
    }

    /** 장비 프리셋만 갈아 끼워 다시 계산한다. 나머지 프리셋은 그날 고른 것을 그대로 쓴다. */
    private CombatPowerHistoryPoint forcedPoint(
            CharacterSnapshot snapshot, LocalDate date, int itemPreset) {
        PresetSelection chosen = dataSheetService.getCombatPresetSelection(snapshot);
        PresetSelection forced = new PresetSelection(
                itemPreset, chosen.abilityPreset(), chosen.hyperStatPreset(), chosen.unionRaiderPreset());
        DataSheet sheet = dataSheetService.getDataSheet(snapshot, forced);
        return toPoint(snapshot, date, sheet, hexaMatrix(snapshot), itemPreset).point();
    }

    private CombatPowerHistoryResponse withRepaired(
            CombatPowerHistoryResponse original, List<CombatPowerHistoryPoint> fixed) {
        Map<LocalDate, CombatPowerHistoryPoint> byDate = fixed.stream()
                .collect(Collectors.toMap(CombatPowerHistoryPoint::date, point -> point));
        List<CombatPowerHistoryPoint> merged = original.points().stream()
                .map(point -> byDate.getOrDefault(point.date(), point))
                .toList();
        return new CombatPowerHistoryResponse(
                original.ocid(), original.range(), original.characterInfo(), original.preset(),
                original.requestedCount(), original.loadedCount(),
                original.truncated(), original.truncatedFrom(), merged);
    }

    /** 최신 → 과거 순으로 지점을 만든다. 빈 응답을 만나면 그 앞까지만 내보낸다. */
    private Flux<CombatPowerHistoryPoint> points(Plan plan) {
        return Flux.fromIterable(plan.dates())
                .flatMapSequential(date -> loadPoint(plan, date), CONCURRENCY)
                .takeWhile(Loaded::exists)
                .map(Loaded::point);
    }

    /**
     * 지점 하나. 지나간 날짜는 캐시를 먼저 본다 — 스냅샷 한 지점이 넥슨 호출 16회라,
     * 일간 30지점을 매번 받으면 480회다.
     *
     * <p>캐시에 두는 것은 43KB 짜리 {@code DataSheet} 이 아니라 숫자 네 개짜리
     * {@link CombatPowerHistoryPoint} 다. 차트가 필요한 건 그게 전부고, 구간을 눌렀을 때
     * 뜨는 상세는 {@code DataSheetService} 쪽 캐시가 따로 맡는다.
     */
    private Mono<Loaded> loadPoint(Plan plan, LocalDate date) {
        // 오늘은 date 파라미터를 붙이면 API 가 거절한다(OPENAPI00004). 계획을 세울 때
        // 이미 받아 둔 현재 스냅샷을 그대로 쓴다. 계속 변하는 값이라 캐시하지 않는다.
        if (date.equals(plan.today())) {
            CombatPowerHistoryPoint point = plan.head().today();
            return Mono.just(point == null ? Loaded.missing() : new Loaded(true, point));
        }

        String cacheKey = historyPointCacheKey(plan.ocid(), date);
        return cache.get(cacheKey, CombatPowerHistoryPoint.class)
                .map(point -> new Loaded(true, point))
                .switchIfEmpty(Mono.defer(() -> snapshotService.getSnapshotByOcid(plan.ocid(), date)
                        .flatMap(snapshot -> loadAndCachePoint(cacheKey, date, snapshot))));
    }

    /** 헥사 코어 문서는 이제 스냅샷이 들고 온다. 따로 부르면 같은 날짜를 두 번 받는다. */
    private JsonNode hexaMatrix(CharacterSnapshot snapshot) {
        return snapshot.document(NexonEndpoint.HEXA_MATRIX);
    }

    /**
     * 빈 응답은 캐시하지 않는다. 아직 열리지 않은 시점일 수 있는데
     * ({@code takeWhile} 이 여기서 추이를 끊는다) 그걸 굳히면 영영 끊긴 채로 남는다.
     */
    private Mono<Loaded> loadAndCachePoint(
            String cacheKey, LocalDate date, CharacterSnapshot snapshot) {
        DataSheet dataSheet = combatDataSheet(snapshot);
        Loaded loaded = toPoint(snapshot, date, dataSheet, hexaMatrix(snapshot));
        if (!loaded.exists()) {
            return Mono.just(loaded);
        }
        Duration ttl = CacheTtlPolicy.forHistoryPoint(date, LocalDateTime.now(KST), dataSheet,
                loaded.point().solErdaFragments() != null);
        return cache.put(cacheKey, loaded.point(), ttl).thenReturn(loaded);
    }

    private Loaded toPoint(
            CharacterSnapshot snapshot, LocalDate date, DataSheet dataSheet, JsonNode hexaMatrix) {
        return toPoint(snapshot, date, dataSheet, hexaMatrix, itemPresetOf(snapshot));
    }

    private Integer itemPresetOf(CharacterSnapshot snapshot) {
        try {
            return dataSheetService.getCombatPresetSelection(snapshot).itemPreset();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Loaded toPoint(
            CharacterSnapshot snapshot, LocalDate date, DataSheet dataSheet, JsonNode hexaMatrix,
            Integer itemPreset) {
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
                dataSheet == null ? null : cooldownSecond(dataSheet),
                dataSheet == null ? null : cooldownSkipPercent(dataSheet),
                itemPreset,
                expired(dataSheet)
        ));
    }

    /**
     * 재사용 대기시간은 전투력식에 안 들어가서 종합 시트에 쌓이지 않는다. 화면에 실으려고
     * 여기서 시트를 한 번 합쳐 꺼낸다 — 전투력은 이미 계산이 끝나 있어 영향이 없다.
     */
    private int cooldownSecond(DataSheet dataSheet) {
        return summed(dataSheet).getCOOLDOWN_SECOND();
    }

    private double cooldownSkipPercent(DataSheet dataSheet) {
        return summed(dataSheet).getCOOLDOWN_SKIP_PERCENT();
    }

    private StatSheet summed(DataSheet dataSheet) {
        if (dataSheet.getSumSheet() == null || dataSheet.getSumSheet().isZero()) {
            dataSheet.buildSum();
        }
        return dataSheet.getSumSheet();
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
        return "maple:history:v7:" + (ocid == null ? "" : ocid.trim()) + ":" + date;
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
        // 빈 이름은 넥슨까지 갈 것도 없다. 그쪽도 400 을 주는데, 우리가 먼저 끊으면
        // 호출 한 번과 500 로그 한 줄을 아낀다.
        if (characterName == null || characterName.isBlank()) {
            return Mono.error(new IllegalArgumentException("캐릭터 이름이 비어 있습니다."));
        }
        LocalDate today = LocalDate.now(KST);
        return ocidService.getOcid(characterName)
                // 없는 이름이면 넥슨이 400 을 준다. 업스트림 URL 이 그대로 새어 나가지 않게 바꾼다.
                .onErrorMap(CombatPowerHistoryService::unknownCharacter,
                        error -> new CharacterNotFoundException(characterName))
                .flatMap(ocid -> todayHead(characterName, ocid, today)
                        .map(head -> buildPlan(ocid, range, today, head)));
    }

    /**
     * 레벨이 낮으면 여기서 끊는다. 30지점을 다 긁고 나서 틀린 값을 보여주느니, 첫 스냅샷에서
     * 알 수 있는 것으로 바로 답한다 — 넥슨 호출 30번을 아끼는 일이기도 하다.
     */
    private void requireHighEnoughLevel(String characterName, TodayHead head) {
        Integer level = head.characterInfo() == null ? null : head.characterInfo().level();
        if (level != null && level < CharacterTooLowException.MINIMUM_LEVEL) {
            throw new CharacterTooLowException(characterName, level);
        }
    }

    private static boolean unknownCharacter(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode().value() == 400;
    }

    /**
     * 오늘치 앞머리. 5분 캐시라 같은 캐릭터를 다시 열거나 구간을 오갈 때는 호출이 없다.
     *
     * <p>레벨이 낮으면 여기서 끊는다. 캐시에서 꺼낸 경우에도 검사해야 한다 - 안 그러면
     * 5분 안에는 260 미만 캐릭터가 그냥 통과한다.
     */
    private Mono<TodayHead> todayHead(String characterName, String ocid, LocalDate today) {
        return cache.getOrLoad(todayHeadCacheKey(ocid), TodayHead.class, TODAY_HEAD_TTL,
                        () -> snapshotService.getCurrentSnapshotByOcid(ocid, today)
                                .map(current -> buildHead(current, today)))
                .doOnNext(head -> requireHighEnoughLevel(characterName, head));
    }

    private TodayHead buildHead(CharacterSnapshot current, LocalDate today) {
        JsonNode basic = current.document(NexonEndpoint.BASIC);
        Loaded loaded = toPoint(current, today, combatDataSheet(current), hexaMatrix(current));
        return new TodayHead(
                characterInfo(basic),
                presetOf(current),
                basicParser.characterCreatedAt(basic),
                loaded.exists() ? loaded.point() : null);
    }

    private CurrentPresetInfo presetOf(CharacterSnapshot current) {
        PresetSelection chosen = dataSheetService.getCombatPresetSelection(current);
        return new CurrentPresetInfo(
                chosen.itemPreset(), chosen.abilityPreset(),
                chosen.hyperStatPreset(), chosen.unionRaiderPreset());
    }

    private static String todayHeadCacheKey(String ocid) {
        return "maple:todayhead:v1:" + (ocid == null ? "" : ocid.trim());
    }

    private Plan buildPlan(String ocid, HistoryRange range, LocalDate today, TodayHead head) {
        LocalDate created = head.createdAt();
        LocalDate floor = created == null || created.isBefore(API_FIRST_DATE) ? API_FIRST_DATE : created;

        List<LocalDate> all = range.dates(today);
        List<LocalDate> kept = all.stream().filter(date -> !date.isBefore(floor)).toList();
        boolean truncated = kept.size() < all.size();
        LocalDate truncatedFrom = truncated ? all.get(kept.size()) : null;

        return new Plan(ocid, today, head, head.characterInfo(), head.preset(), kept, truncated, truncatedFrom);
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
        String code = error instanceof CharacterNotFoundException ? "NOT_FOUND"
                : error instanceof CharacterTooLowException ? "TOO_LOW"
                : "ERROR";
        return new HistoryEvents.Error(code, message(error));
    }

    /**
     * 화면에 내보낼 문구.
     *
     * <p><b>우리가 만든 예외만 그대로 쓴다.</b> 그 밖(넥슨 5xx·타임아웃)은 고정 문구로 바꾼다 —
     * {@code WebClientResponseException} 의 메시지에는 요청 URI 가 통째로 들어 있어서,
     * 넥슨이 흔들릴 때 아무나 우리 업스트림 구성을 그대로 볼 수 있다. 키는 헤더라 안 새지만
     * 굳이 알릴 것도 아니다. 원문은 로그에만 남긴다.
     */
    private String message(Throwable error) {
        if (error instanceof CharacterNotFoundException || error instanceof CharacterTooLowException) {
            return error.getMessage();
        }
        log.warn("추이 조회 실패", error);
        return "조회에 실패했습니다. 잠시 뒤 다시 시도해 주세요.";
    }

    private ServerSentEvent<Object> event(String name, Object payload) {
        return ServerSentEvent.builder().event(name).data(payload).build();
    }

    private record Plan(
            String ocid,
            LocalDate today,
            TodayHead head,
            CharacterInfo characterInfo,
            CurrentPresetInfo preset,
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
