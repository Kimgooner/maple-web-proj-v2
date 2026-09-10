package org.whitedoggy.mapleweb2.analysis.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.analysis.service.OcidService;
import org.whitedoggy.mapleweb2.analysis.service.SnapshotService;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.validation.RankingCharacterClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 랭킹에서 레벨 구간별 표본을 뽑아 전투력 분포를 만든다.
 *
 * <p>종합 랭킹은 레벨 내림차순이라 한 구간이 연속된 페이지 범위를 차지한다. 경계는
 * 이진 탐색으로 찾는다 — 레벨은 API 응답에 있지만 "몇 레벨이 몇 위부터인지"는 없다.
 *
 * <p><b>페이지를 넓게 흩어 뽑는 것이 핵심이다.</b> 한 페이지는 200명이 전부 같은
 * 레벨이고 랭킹까지 붙어 있다. 몇 페이지만 읽고 거기서 다 채우면 구간 안의 특정
 * 레벨·특정 순위대만 담긴 표본이 되어 분포가 통째로 밀린다. 그래서 구간마다
 * {@value #PAGES_PER_BAND} 쪽을 고르게 흩어 읽고 각 쪽에서 {@value #PICKS_PER_PAGE}
 * 명만 가져온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LevelBandCollector {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 5레벨 단위. 레벨 높은 쪽부터 — 경계 이진 탐색이 앞 결과를 하한으로 이어 쓴다. */
    private static final List<int[]> BANDS = List.of(
            new int[]{295, 300}, new int[]{290, 294}, new int[]{285, 289}, new int[]{280, 284},
            new int[]{275, 279}, new int[]{270, 274}, new int[]{265, 269}, new int[]{260, 264});

    /** 종합 랭킹의 마지막 쪽. 이보다 뒤는 빈 응답이다. */
    private static final int LAST_PAGE = 32_767;
    private static final int PAGES_PER_BAND = 100;
    private static final int PICKS_PER_PAGE = 10;

    /**
     * 한 명당 API 18회(ocid 1 + 스냅샷 17)를 한꺼번에 쏜다. 10 으로 두면 초당 230건까지 올라가
     * 서비스 키 한도(500/s)의 절반을 배치 혼자 10분간 쓴다. 약관 제5조 ⑧ 이 주기적 접속으로
     * 과부하를 내는 것을 막고 있고, 새벽에 30분 더 걸리는 건 아무 문제가 아니라 낮게 잡는다.
     */
    private static final int CONCURRENCY = 4;

    /** 계산이 완성되지 않은 직업. 분포에 넣으면 그 구간이 통째로 낮아진다. */
    private static final String UNSUPPORTED_CLASS = "데몬어벤져";

    private final RankingCharacterClient rankingCharacterClient;
    private final OcidService ocidService;
    private final SnapshotService snapshotService;
    private final DataSheetService dataSheetService;
    private final BasicParser basicParser;

    public Mono<LevelBandSnapshot> collect(LocalDate rankingDate, LocalDate weekOf) {
        Random random = new Random(weekOf.toEpochDay());
        return boundaries(rankingDate)
                .flatMap(bounds -> Flux.fromIterable(BANDS)
                        .index()
                        .concatMap(entry -> collectBand(
                                rankingDate,
                                entry.getT2(),
                                bounds.get(entry.getT1().intValue()),
                                bounds.get(entry.getT1().intValue() + 1) - 1,
                                random))
                        .collectList())
                .map(bands -> new LevelBandSnapshot(weekOf, rankingDate, bands));
    }

    /**
     * 구간 경계 페이지. 결과의 i번째는 {@code BANDS[i]} 가 시작하는 쪽이고, 마지막 하나는
     * 가장 낮은 구간 바로 아래가 시작하는 쪽이다. 그래서 길이가 구간 수보다 하나 많다.
     *
     * <p>레벨 X 는 {@code firstPageAtMost(X)} 부터 {@code firstPageAtMost(X-1) - 1} 까지다.
     * 구간 하한을 잡을 때 {@code lo} 가 아니라 {@code lo - 1} 을 넣어야 하는 이유다.
     */
    private Mono<List<Integer>> boundaries(LocalDate date) {
        List<Integer> found = new ArrayList<>();
        found.add(1);
        Mono<List<Integer>> chain = Mono.just(found);
        for (int[] band : BANDS) {
            int below = band[0] - 1;
            chain = chain.flatMap(acc -> firstPageAtMost(date, below, acc.get(acc.size() - 1), LAST_PAGE)
                    .map(page -> {
                        acc.add(page);
                        return acc;
                    }));
        }
        return chain.doOnNext(bounds -> log.info("레벨 구간 경계 페이지: {}", bounds));
    }

    /** 첫 줄의 레벨이 {@code level} 이하인 첫 쪽. 랭킹이 내림차순이라 단조라서 이진 탐색이 선다. */
    private Mono<Integer> firstPageAtMost(LocalDate date, int level, int low, int high) {
        if (low >= high) {
            return Mono.just(low);
        }
        int mid = low + (high - low) / 2;
        return topLevel(date, mid).flatMap(top -> top <= level
                ? firstPageAtMost(date, level, low, mid)
                : firstPageAtMost(date, level, mid + 1, high));
    }

    /** 그 날짜의 랭킹이 열려 있는가. 배치가 도는 시각에 어제 것이 아직 안 열려 있을 수 있다. */
    public Mono<Boolean> hasRanking(LocalDate date) {
        return topLevel(date, 1).map(level -> level > 0);
    }

    /** 빈 쪽(마지막 쪽 너머)은 0 으로 본다 — 어떤 기준 레벨보다도 작아 왼쪽으로 좁혀진다. */
    private Mono<Integer> topLevel(LocalDate date, int page) {
        return rankingCharacterClient.getOverallRanking(date, page)
                .map(response -> {
                    JsonNode rows = response.path("ranking");
                    return rows.isEmpty() ? 0 : rows.get(0).path("character_level").asInt(0);
                })
                .onErrorReturn(0);
    }

    private Mono<LevelBandStats> collectBand(
            LocalDate date, int[] band, int firstPage, int lastPage, Random random) {
        List<Integer> pages = spreadPages(firstPage, lastPage, random);
        return Flux.fromIterable(pages)
                .concatMap(page -> namesOnPage(date, page, band, random))
                .collectList()
                .map(chunks -> chunks.stream().flatMap(List::stream).distinct().toList())
                .flatMap(names -> Flux.fromIterable(names)
                        .flatMap(name -> combatPowerOf(name, band), CONCURRENCY)
                        .collectSortedList()
                        .map(values -> {
                            LevelBandStats stats = summarize(band, values);
                            log.info("레벨 {}~{}: 표본 {}명 중 {}명 사용, 중앙값 {}",
                                    band[0], band[1], names.size(), stats.sampleSize(), stats.median());
                            return stats;
                        }));
    }

    /** 구간을 {@value #PAGES_PER_BAND} 칸으로 나눠 칸마다 한 쪽씩 — 한쪽에 몰리지 않게. */
    private List<Integer> spreadPages(int firstPage, int lastPage, Random random) {
        int total = lastPage - firstPage + 1;
        if (total <= PAGES_PER_BAND) {
            List<Integer> all = new ArrayList<>();
            for (int page = firstPage; page <= lastPage; page++) {
                all.add(page);
            }
            return all;
        }
        Set<Integer> pages = new LinkedHashSet<>();
        for (int slot = 0; slot < PAGES_PER_BAND; slot++) {
            int start = firstPage + (int) ((long) total * slot / PAGES_PER_BAND);
            int end = firstPage + (int) ((long) total * (slot + 1) / PAGES_PER_BAND) - 1;
            pages.add(end <= start ? start : start + random.nextInt(end - start + 1));
        }
        return List.copyOf(pages);
    }

    private Mono<List<String>> namesOnPage(LocalDate date, int page, int[] band, Random random) {
        return rankingCharacterClient.getOverallRanking(date, page)
                .map(response -> {
                    List<String> names = new ArrayList<>();
                    for (JsonNode row : response.path("ranking")) {
                        int level = row.path("character_level").asInt(0);
                        if (level >= band[0] && level <= band[1]) {
                            names.add(row.path("character_name").asText(""));
                        }
                    }
                    Collections.shuffle(names, random);
                    return names.size() <= PICKS_PER_PAGE ? names : names.subList(0, PICKS_PER_PAGE);
                })
                .onErrorReturn(List.of());
    }

    /**
     * 한 명의 전투력. 못 읽거나 표본으로 쓸 수 없으면 빈 결과다 — 한 명 때문에 배치가
     * 멈추지 않는다.
     *
     * <p>전투력은 {@code date} 없이 부른 <b>지금</b> 값이다. 명단은 어제(혹은 그저께) 랭킹에서
     * 뽑았으므로 그사이 레벨이 오른 사람이 있다. 그대로 두면 279 로 뽑힌 281 짜리가 275~279
     * 구간에 섞여 위쪽 경계가 조금씩 부풀려진다. 스냅샷에 레벨이 이미 들어 있으니 여기서
     * 다시 보고 벗어난 사람을 뺀다 — 호출은 늘지 않는다.
     */
    private Mono<Long> combatPowerOf(String characterName, int[] band) {
        LocalDate today = LocalDate.now(KST);
        return ocidService.getOcid(characterName)
                .flatMap(ocid -> snapshotService.getCurrentSnapshotByOcid(ocid, today))
                .mapNotNull(snapshot -> {
                    JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
                    Integer level = basicParser.characterLevel(basic);
                    if (level == null || level < band[0] || level > band[1]) {
                        return null;
                    }
                    if (UNSUPPORTED_CLASS.equals(basicParser.characterClass(basic))) {
                        return null;
                    }
                    DataSheet sheet = dataSheetService.getCurrentDataSheet(snapshot);
                    if (sheet.isIncompleteSnapshot() || sheet.isWeaponMissing()
                            || sheet.isUnknownWeapon() || sheet.isWeaponNormalizationFailed()) {
                        return null;
                    }
                    Long combatPower = sheet.getCombatPower();
                    return combatPower == null || combatPower <= 0 ? null : combatPower;
                })
                .onErrorResume(error -> Mono.empty());
    }

    /** {@code values} 는 오름차순. 상위 p% 는 아래에서 (100-p)% 지점을 선형 보간해 잡는다. */
    private LevelBandStats summarize(int[] band, List<Long> values) {
        if (values.isEmpty()) {
            return new LevelBandStats(band[0], band[1], 0, 0, 0, 0, 0, 0);
        }
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return new LevelBandStats(
                band[0], band[1], values.size(),
                percentile(values, 1), percentile(values, 10), percentile(values, 30),
                percentile(values, 50), sum / values.size());
    }

    private long percentile(List<Long> ascending, int topPercent) {
        double at = (ascending.size() - 1) * (1 - topPercent / 100.0);
        int low = (int) Math.floor(at);
        int high = Math.min(low + 1, ascending.size() - 1);
        return Math.round(ascending.get(low) + (ascending.get(high) - ascending.get(low)) * (at - low));
    }
}
