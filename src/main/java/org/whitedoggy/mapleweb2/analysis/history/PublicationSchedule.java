package org.whitedoggy.mapleweb2.analysis.history;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 넥슨이 전일 데이터를 여는 시각.
 *
 * <p>공지: 전일 데이터는 다음날 오전 2시(KST)부터 조회할 수 있다
 * (https://openapi.nexon.com/support/notice/2597202/). 그 전에 어제를 물으면 200 에 빈 문서가
 * 온다. 이걸 "캐릭터가 없던 날"과 같이 다루면 00시~02시엔 추이가 오늘 한 점만 남는다.
 */
public final class PublicationSchedule {

    /** 전일 데이터가 열리는 시각(KST). {@code CacheTtlPolicy.PREVIOUS_DAY_OPENS_AT} 과 같은 값이다. */
    public static final LocalTime OPENS_AT = LocalTime.of(2, 0);

    private PublicationSchedule() {
    }

    /** 이 날짜가 아직 안 열린 어제인가 — 물어봐야 빈 문서라 묻지 않는다. */
    public static boolean isAwaiting(LocalDate date, LocalDateTime nowKst) {
        return isYesterday(date, nowKst.toLocalDate()) && nowKst.toLocalTime().isBefore(OPENS_AT);
    }

    public static boolean isYesterday(LocalDate date, LocalDate todayKst) {
        return date.equals(todayKst.minusDays(1));
    }
}
