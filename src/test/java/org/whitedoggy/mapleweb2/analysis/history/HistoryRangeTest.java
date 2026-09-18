package org.whitedoggy.mapleweb2.analysis.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryRangeTest {

    /** 오른쪽 끝은 오늘, 그 앞은 지난달부터 달마다 1일. 오늘 포함 12개. */
    @Test
    void 월간은_오늘로_끝나고_앞은_각_달_1일이다() {
        List<LocalDate> dates = HistoryRange.MONTHLY.dates(LocalDate.of(2026, 9, 18));
        assertThat(dates).hasSize(12);
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(dates.get(1)).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(dates.get(11)).isEqualTo(LocalDate.of(2025, 10, 1));
    }

    /** 오늘이 1일이면 종전과 같은 목록이다. */
    @Test
    void 오늘이_1일이면_그대로_1일_열둘이다() {
        List<LocalDate> dates = HistoryRange.MONTHLY.dates(LocalDate.of(2026, 9, 1));
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(dates.get(1)).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(dates).hasSize(12);
    }

    @Test
    void 일간은_오늘_포함_30일이다() {
        List<LocalDate> dates = HistoryRange.DAILY.dates(LocalDate.of(2026, 9, 18));
        assertThat(dates).hasSize(30);
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(dates.get(29)).isEqualTo(LocalDate.of(2026, 8, 20));
    }
}
