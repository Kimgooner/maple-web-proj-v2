package org.whitedoggy.mapleweb2.domain.combat.data;

import java.util.List;
import java.util.Map;

public final class JobStatTable {
    public static final Map<String, JobStatProfile> JOBS = Map.ofEntries(
            Map.entry("히어로", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("팔라딘", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("다크나이트", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("아크메이지(불,독)", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("아크메이지(썬,콜)", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("비숍", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("보우마스터", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("신궁", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("패스파인더", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("나이트로드", new JobStatProfile(List.of("LUK"), List.of("DEX"))),
            Map.entry("섀도어", new JobStatProfile(List.of("LUK"), List.of("DEX", "STR"))),
            Map.entry("듀얼블레이더", new JobStatProfile(List.of("LUK"), List.of("DEX", "STR"))),
            Map.entry("바이퍼", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("캡틴", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("캐논슈터", new JobStatProfile(List.of("STR"), List.of("DEX"))),

            Map.entry("소울마스터", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("플레임위자드", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("윈드브레이커", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("나이트워커", new JobStatProfile(List.of("LUK"), List.of("DEX"))),
            Map.entry("스트라이커", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("미하일", new JobStatProfile(List.of("STR"), List.of("DEX"))),

            Map.entry("블래스터", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("배틀메이지", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("와일드헌터", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("메카닉", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("제논", new JobStatProfile(List.of("STR", "DEX", "LUK"), List.of())),
            Map.entry("데몬슬레이어", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("데몬어벤져", new JobStatProfile(List.of("HP"), List.of("STR"))),

            Map.entry("아란", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("에반", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("메르세데스", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("팬텀", new JobStatProfile(List.of("LUK"), List.of("DEX"))),
            Map.entry("루미너스", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("은월", new JobStatProfile(List.of("STR"), List.of("DEX"))),

            Map.entry("카이저", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("엔젤릭버스터", new JobStatProfile(List.of("DEX"), List.of("STR"))),
            Map.entry("카데나", new JobStatProfile(List.of("LUK"), List.of("STR", "DEX"))),
            Map.entry("카인", new JobStatProfile(List.of("DEX"), List.of("STR"))),

            Map.entry("아델", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("일리움", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("아크", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("칼리", new JobStatProfile(List.of("LUK"), List.of("DEX"))),

            Map.entry("호영", new JobStatProfile(List.of("LUK"), List.of("DEX"))),
            Map.entry("라라", new JobStatProfile(List.of("INT"), List.of("LUK"))),
            Map.entry("렌", new JobStatProfile(List.of("STR"), List.of("DEX"))),

            Map.entry("제로", new JobStatProfile(List.of("STR"), List.of("DEX"))),
            Map.entry("키네시스", new JobStatProfile(List.of("INT"), List.of("LUK")))
    );

    private JobStatTable() {
    }

    public record JobStatProfile(
            List<String> mainStats,
            List<String> subStats
    ) {
    }
}
