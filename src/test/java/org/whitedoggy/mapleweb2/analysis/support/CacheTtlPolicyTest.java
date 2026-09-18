package org.whitedoggy.mapleweb2.analysis.support;

import org.junit.jupiter.api.Test;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheTtlPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 14, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Test
    void todayIsShortLivedBecauseItKeepsChanging() {
        assertEquals(CacheTtlPolicy.TODAY, CacheTtlPolicy.forDataSheet(TODAY, NOW, settledSheet()));
    }

    @Test
    void settledPastIsKeptLong() {
        assertEquals(CacheTtlPolicy.SETTLED_PAST,
                CacheTtlPolicy.forDataSheet(TODAY.minusDays(10), NOW, settledSheet()));
    }

    @Test
    void pastWithMissingDocumentsIsRetriedSoon() {
        DataSheet sheet = settledSheet();
        sheet.setIncompleteSnapshot(true);

        assertEquals(CacheTtlPolicy.UNSETTLED,
                CacheTtlPolicy.forDataSheet(TODAY.minusDays(10), NOW, sheet));
    }

    @Test
    void pastWithoutWeaponIsRetriedSoon() {
        DataSheet sheet = settledSheet();
        sheet.setWeaponMissing(true);

        assertEquals(CacheTtlPolicy.UNSETTLED,
                CacheTtlPolicy.forDataSheet(TODAY.minusDays(10), NOW, sheet));
    }

    @Test
    void pastWithoutCombatPowerIsRetriedSoon() {
        DataSheet sheet = settledSheet();
        sheet.setCombatPower(null);

        assertEquals(CacheTtlPolicy.UNSETTLED,
                CacheTtlPolicy.forDataSheet(TODAY.minusDays(10), NOW, sheet));
    }

    /** 전일 데이터는 02:00 KST 부터 열린다. 그전에 받은 어제는 온전해 보여도 믿지 않는다. */
    @Test
    void yesterdayBeforeTwoAmIsRetriedSoon() {
        LocalDateTime beforeOpen = LocalDateTime.of(2026, 9, 8, 1, 59);

        assertEquals(CacheTtlPolicy.UNSETTLED,
                CacheTtlPolicy.forDataSheet(LocalDate.of(2026, 9, 7), beforeOpen, settledSheet()));
    }

    @Test
    void yesterdayAfterTwoAmIsKeptLong() {
        LocalDateTime afterOpen = LocalDateTime.of(2026, 9, 8, 2, 0);

        assertEquals(CacheTtlPolicy.SETTLED_PAST,
                CacheTtlPolicy.forDataSheet(LocalDate.of(2026, 9, 7), afterOpen, settledSheet()));
    }

    /** 02:00 전이라도 그저께는 이미 열려 있다. */
    @Test
    void dayBeforeYesterdayIsKeptLongEvenBeforeTwoAm() {
        LocalDateTime beforeOpen = LocalDateTime.of(2026, 9, 8, 1, 59);

        assertEquals(CacheTtlPolicy.SETTLED_PAST,
                CacheTtlPolicy.forDataSheet(LocalDate.of(2026, 9, 6), beforeOpen, settledSheet()));
    }

    private static DataSheet settledSheet() {
        DataSheet sheet = new DataSheet();
        sheet.setCombatPower(123_456_789L);
        return sheet;
    }

    /** 어떤 구간도 더는 읽지 않는 날 이후로는 들고 있지 않는다. */
    @Test
    void 지점은_마지막으로_읽히는_날까지만_둔다() {
        // 격자 밖 날짜는 주간(7일)만 읽는다. 9/8 에 9/7(격자 밖이면) 지점은 9/14 자정까지.
        LocalDate offGrid = TODAY.minusDays(1);
        while (org.whitedoggy.mapleweb2.analysis.history.HistoryRange.isOnGrid(offGrid)) offGrid = offGrid.minusDays(1);
        assertEquals(java.time.Duration.between(NOW, offGrid.plusDays(7).atStartOfDay()),
                CacheTtlPolicy.forHistoryPoint(offGrid, NOW, settledSheet(), true));

        // 격자 날짜는 월간(30일)이 읽는다. 30일 상한과 창 상한 중 짧은 쪽.
        LocalDate onGrid = TODAY.minusDays(2);
        while (!org.whitedoggy.mapleweb2.analysis.history.HistoryRange.isOnGrid(onGrid) || onGrid.getDayOfMonth() == 1) onGrid = onGrid.minusDays(1);
        java.time.Duration expected = java.time.Duration.between(NOW, onGrid.plusDays(30).atStartOfDay());
        if (expected.compareTo(CacheTtlPolicy.SETTLED_PAST) > 0) expected = CacheTtlPolicy.SETTLED_PAST;
        assertEquals(expected, CacheTtlPolicy.forHistoryPoint(onGrid, NOW, settledSheet(), true));
    }

    /** 1일 지점은 연간 추이가 계속 읽으므로 창 상한을 받지 않는다. */
    @Test
    void 매달_1일_지점은_그대로_30일이다() {
        assertEquals(CacheTtlPolicy.SETTLED_PAST,
                CacheTtlPolicy.forHistoryPoint(LocalDate.of(2026, 6, 1), NOW, settledSheet(), true));
    }

    /** 이미 창 밖인 날짜(프리셋 되돌리기)는 짧게만 둔다. */
    @Test
    void 이미_창_밖인_지점은_짧게_둔다() {
        assertEquals(CacheTtlPolicy.UNSETTLED,
                CacheTtlPolicy.forHistoryPoint(TODAY.minusDays(40), NOW, settledSheet(), true));
    }
}
