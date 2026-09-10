package org.whitedoggy.mapleweb2.analysis.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 레벨 구간별 전투력 분포를 주 단위로 재어 캐시에 쌓는다.
 *
 * <p>매주 월요일 00시(KST)에 한 번 돈다. 그 시각에는 어제 랭킹이 아직 안 열려 있어
 * (전일 데이터는 다음날 02시부터) 열려 있는 가장 최근 날짜를 찾아 쓴다.
 *
 * <p>과거 주를 지우지 않고 쌓는다. 추이 화면이 1년 전 지점 옆에 기준선을 그으려면
 * 그때의 분포가 있어야 하고, 한 주치가 몇 백 바이트라 지울 이유가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevelBandStatsService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /**
     * 캐시가 아니라 저장소다. 다른 키와 달리 만료를 걸지 않는다 — 지나간 주는 다시 잴 수
     * 없고(랭킹은 그날의 것이다), 밀려나면 부팅 때 다시 긁느라 배포마다 8,000명을 다시
     * 재게 된다. 프로덕션 Redis 가 {@code volatile-lru} 라 만료 없는 키는 남는다.
     */
    private static final String CACHE_KEY = "maple:levelband:v1:weeks";
    private static final int KEEP_WEEKS = 60;

    /** 랭킹이 열려 있는 날짜를 오늘 기준으로 며칠까지 거슬러 찾아볼 것인가. */
    private static final int RANKING_DATE_LOOKBACK = 4;

    private final MapleCache cache;
    private final LevelBandCollector collector;

    /**
     * 배치를 켤지. 로컬·테스트에서 8,000명을 긁는 일이 없도록 기본은 꺼짐이고,
     * 프로덕션 compose 에서 {@code MAPLE_LEVEL_BAND_ENABLED=true} 로 켠다.
     */
    @Value("${maple.level-band.enabled:false}")
    private boolean enabled;

    /** 부팅 직후 채우기와 월요일 배치가 겹쳐 두 번 돌지 않게 한다. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Mono<LevelBandStatsResponse> getStats() {
        return cache.get(CACHE_KEY, LevelBandStatsResponse.class)
                .defaultIfEmpty(new LevelBandStatsResponse(List.of()));
    }

    /** 매주 월요일 00:00 KST. */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    public void collectWeekly() {
        if (!enabled) {
            return;
        }
        refresh().subscribe();
    }

    /**
     * 아직 한 주도 없으면 부팅 뒤에 한 번 채운다. 새 서버를 올린 주에 다음 월요일까지
     * 화면에 기준선이 하나도 안 나오는 일을 막는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void collectOnFirstBoot() {
        if (!enabled) {
            return;
        }
        getStats()
                .filter(stats -> stats.weeks().isEmpty())
                .flatMap(ignored -> refresh())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    public Mono<LevelBandSnapshot> refresh() {
        if (!running.compareAndSet(false, true)) {
            log.info("레벨 구간 표본 수집이 이미 돌고 있어 건너뜁니다");
            return Mono.empty();
        }
        LocalDate today = LocalDate.now(KST);
        LocalDate weekOf = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long startedAt = System.currentTimeMillis();
        return openRankingDate(today)
                .flatMap(rankingDate -> collector.collect(rankingDate, weekOf))
                .flatMap(snapshot -> store(snapshot).thenReturn(snapshot))
                .doOnSuccess(snapshot -> log.info("레벨 구간 표본 수집 완료: {} 기준, {}초 걸림",
                        snapshot.rankingDate(), (System.currentTimeMillis() - startedAt) / 1000))
                .doOnError(error -> log.warn("레벨 구간 표본 수집 실패", error))
                .doFinally(ignored -> running.set(false));
    }

    /**
     * 랭킹이 열려 있는 가장 최근 날짜. 어제부터 하루씩 거슬러 첫 줄이 있는 날을 고른다.
     */
    private Mono<LocalDate> openRankingDate(LocalDate today) {
        return Flux.fromStream(Stream.iterate(today.minusDays(1), date -> date.minusDays(1))
                        .limit(RANKING_DATE_LOOKBACK))
                .concatMap(date -> collector.hasRanking(date).filter(open -> open).map(open -> date))
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("열려 있는 랭킹 날짜를 찾지 못했습니다")));
    }

    private Mono<LevelBandStatsResponse> store(LevelBandSnapshot snapshot) {
        return getStats().flatMap(stats -> {
            List<LevelBandSnapshot> weeks = new ArrayList<>(stats.weeks().stream()
                    .filter(week -> !week.weekOf().equals(snapshot.weekOf()))
                    .toList());
            weeks.add(snapshot);
            weeks.sort(Comparator.comparing(LevelBandSnapshot::weekOf));
            if (weeks.size() > KEEP_WEEKS) {
                weeks = new ArrayList<>(weeks.subList(weeks.size() - KEEP_WEEKS, weeks.size()));
            }
            return cache.put(CACHE_KEY, new LevelBandStatsResponse(List.copyOf(weeks)));
        });
    }
}
