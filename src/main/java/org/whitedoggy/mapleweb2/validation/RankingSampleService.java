package org.whitedoggy.mapleweb2.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.validation.dto.RankingCharacterSample;
import org.whitedoggy.mapleweb2.validation.dto.RankingSampleGroup;
import org.whitedoggy.mapleweb2.validation.dto.RankingSampleResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RankingSampleService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE_PER_FILTER = 2;

    private final RankingCharacterClient rankingCharacterClient;

    public Mono<RankingSampleResponse> getSamples() {
        LocalDate rankingDate = LocalDate.now(KST);
        return Flux.fromIterable(targetGroups())
                .concatMap(group -> collectGroup(rankingDate, group))
                .collectList()
                .map(groups -> new RankingSampleResponse(rankingDate, groups));
    }

    private Mono<RankingSampleGroup> collectGroup(LocalDate date, RankingTargetGroup group) {
        if (group.onePerClassFilter()) {
            return collectOnePerClassFilter(date, group);
        }

        Map<String, RankingCharacterSample> samples = new LinkedHashMap<>();

        return Flux.fromIterable(group.classFilters())
                .concatMap(classFilter -> fetchFilterSamples(date, classFilter, group)
                        .doOnNext(sample -> samples.putIfAbsent(sample.characterName(), sample)))
                .takeUntil(ignored -> samples.size() >= group.requestedCount())
                .then(Mono.fromSupplier(() -> new RankingSampleGroup(
                        group.groupName(),
                        group.requestedCount(),
                        samples.values().stream().limit(group.requestedCount()).toList()
                )));
    }

    private Mono<RankingSampleGroup> collectOnePerClassFilter(LocalDate date, RankingTargetGroup group) {
        return Flux.fromIterable(group.classFilters())
                .concatMap(classFilter -> fetchFilterSamples(date, classFilter, group).next())
                .collectList()
                .map(samples -> new RankingSampleGroup(
                        group.groupName(),
                        group.requestedCount(),
                        samples
                ));
    }

    private Flux<RankingCharacterSample> fetchFilterSamples(
            LocalDate date,
            String classFilter,
            RankingTargetGroup group
    ) {
        return Flux.range(1, MAX_PAGE_PER_FILTER)
                .concatMap(page -> rankingCharacterClient.getOverallRanking(date, classFilter, page)
                        .onErrorResume(ignored -> Mono.empty())
                        .flatMapMany(response -> parseSamples(response, group)));
    }

    private Flux<RankingCharacterSample> parseSamples(JsonNode response, RankingTargetGroup group) {
        List<RankingCharacterSample> samples = new ArrayList<>();
        for (JsonNode ranking : response.path("ranking")) {
            RankingCharacterSample sample = toSample(ranking);
            if (group.matches(sample)) {
                samples.add(sample);
            }
        }
        return Flux.fromIterable(samples);
    }

    private RankingCharacterSample toSample(JsonNode ranking) {
        return new RankingCharacterSample(
                ranking.path("character_name").asText(""),
                ranking.path("world_name").asText(""),
                ranking.path("class_name").asText(""),
                ranking.path("sub_class_name").asText(""),
                ranking.path("character_level").asInt(0),
                ranking.path("ranking").asInt(0)
        );
    }

    private List<RankingTargetGroup> targetGroups() {
        return List.of(
                new RankingTargetGroup(
                        "데몬어벤져",
                        List.of("레지스탕스-데몬어벤져"),
                        List.of("데몬어벤져"),
                        5,
                        false
                ),
                new RankingTargetGroup(
                        "제논",
                        List.of("레지스탕스-제논"),
                        List.of("제논"),
                        5,
                        false
                ),
                new RankingTargetGroup(
                        "제로",
                        List.of("초월자-제로"),
                        List.of("제로"),
                        5,
                        false
                ),
                new RankingTargetGroup(
                        "STR",
                        List.of(
                                "전사-히어로",
                                "전사-팔라딘",
                                "전사-다크나이트",
                                "아델-전체 전직",
                                "카이저-전체 전직",
                                "아크-전체 전직",
                                "해적-바이퍼"
                        ),
                        List.of("히어로", "팔라딘", "다크나이트", "아델", "카이저", "아크", "바이퍼"),
                        7,
                        true
                ),
                new RankingTargetGroup(
                        "DEX",
                        List.of(
                                "궁수-보우마스터",
                                "궁수-신궁",
                                "궁수-패스파인더",
                                "카인-전체 전직",
                                "메르세데스-전체 전직",
                                "레지스탕스-와일드헌터",
                                "레지스탕스-메카닉",
                                "해적-캡틴"
                        ),
                        List.of("보우마스터", "신궁", "패스파인더", "카인", "메르세데스", "와일드헌터", "메카닉", "캡틴"),
                        8,
                        true
                ),
                new RankingTargetGroup(
                        "LUK",
                        List.of(
                                "도적-나이트로드",
                                "도적-섀도어",
                                "도적-듀얼블레이더",
                                "팬텀-전체 전직",
                                "호영-전체 전직",
                                "칼리-전체 전직",
                                "카데나-전체 전직"
                        ),
                        List.of("나이트로드", "섀도어", "듀얼블레이더", "팬텀", "호영", "칼리", "카데나"),
                        7,
                        true
                ),
                new RankingTargetGroup(
                        "INT",
                        List.of(
                                "마법사-아크메이지(불,독)",
                                "마법사-아크메이지(썬,콜)",
                                "마법사-비숍",
                                "라라-전체 전직",
                                "프렌즈 월드-키네시스",
                                "일리움-전체 전직",
                                "루미너스-전체 전직",
                                "에반-전체 전직"
                        ),
                        List.of("아크메이지", "비숍", "라라", "키네시스", "일리움", "루미너스", "에반"),
                        8,
                        true
                )
        );
    }

    private record RankingTargetGroup(
            String groupName,
            List<String> classFilters,
            List<String> classNameKeywords,
            int requestedCount,
            boolean onePerClassFilter
    ) {
        private boolean matches(RankingCharacterSample sample) {
            String className = sample.className() == null ? "" : sample.className();
            String subClassName = sample.subClassName() == null ? "" : sample.subClassName();
            return classNameKeywords.stream()
                    .anyMatch(keyword -> className.contains(keyword) || subClassName.contains(keyword));
        }
    }
}
