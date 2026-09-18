package org.whitedoggy.mapleweb2.analysis.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationScheduleTest {

    /** 9/16 01:59 에 9/15 는 아직 안 열렸고, 02:00 부터 열린다. 9/14 는 언제나 열려 있다. */
    @Test
    void 어제는_02시_전까지_집계_대기다() {
        LocalDate yesterday = LocalDate.of(2026, 9, 15);
        assertThat(PublicationSchedule.isAwaiting(yesterday, LocalDateTime.of(2026, 9, 16, 1, 59))).isTrue();
        assertThat(PublicationSchedule.isAwaiting(yesterday, LocalDateTime.of(2026, 9, 16, 2, 0))).isFalse();
        assertThat(PublicationSchedule.isAwaiting(LocalDate.of(2026, 9, 14), LocalDateTime.of(2026, 9, 16, 1, 0))).isFalse();
        assertThat(PublicationSchedule.isAwaiting(LocalDate.of(2026, 9, 16), LocalDateTime.of(2026, 9, 16, 1, 0))).isFalse();
    }

    @Test
    void 집계_대기_지점은_날짜만_있고_값이_없다() {
        CombatPowerHistoryPoint point = CombatPowerHistoryPoint.awaiting(LocalDate.of(2026, 9, 15));
        assertThat(point.awaiting()).isTrue();
        assertThat(point.combatPower()).isNull();
        assertThat(point.level()).isNull();
    }
}
