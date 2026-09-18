package org.whitedoggy.mapleweb2.analysis.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryRangeTest {

    @Test
    void 주간은_오늘_포함_7일_매일이다() {
        List<LocalDate> dates = HistoryRange.WEEKLY.dates(LocalDate.of(2026, 9, 18));
        assertThat(dates).hasSize(7);
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(dates.get(6)).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    /** 격자는 달력에 고정이라 내일 물어도 같은 날들이 나온다 — 어제 만든 지점을 오늘도 쓴다. */
    @Test
    void 월간은_오늘과_3일_격자_날들이고_격자는_달력에_고정이다() {
        LocalDate today = LocalDate.of(2026, 9, 18);
        List<LocalDate> dates = HistoryRange.MONTHLY.dates(today);
        assertThat(dates.get(0)).isEqualTo(today);
        assertThat(dates.size()).isBetween(10, 11);
        assertThat(dates.subList(1, dates.size())).allMatch(HistoryRange::isOnGrid);
        assertThat(dates.get(dates.size() - 1)).isAfterOrEqualTo(today.minusDays(29));

        List<LocalDate> tomorrow = HistoryRange.MONTHLY.dates(today.plusDays(1));
        // 오늘 목록의 격자 날은 (창을 벗어난 맨 끝 하나를 빼면) 내일 목록에도 그대로 있다.
        assertThat(tomorrow).containsAll(dates.subList(1, dates.size() - 1));
    }

    /** 오른쪽 끝은 오늘, 그 앞은 지난달부터 달마다 1일. 오늘 포함 12개. */
    @Test
    void 연간은_오늘로_끝나고_앞은_각_달_1일이다() {
        List<LocalDate> dates = HistoryRange.YEARLY.dates(LocalDate.of(2026, 9, 18));
        assertThat(dates).hasSize(12);
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(dates.get(1)).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(dates.get(11)).isEqualTo(LocalDate.of(2025, 10, 1));
        // 오늘이 1일이면 종전과 같은 목록이다.
        assertThat(HistoryRange.YEARLY.dates(LocalDate.of(2026, 9, 1)).get(1)).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void 옛_화면의_daily_는_주간으로_받는다() {
        assertThat(HistoryRange.from("daily")).isEqualTo(HistoryRange.WEEKLY);
        assertThat(HistoryRange.from("Yearly")).isEqualTo(HistoryRange.YEARLY);
    }
}
