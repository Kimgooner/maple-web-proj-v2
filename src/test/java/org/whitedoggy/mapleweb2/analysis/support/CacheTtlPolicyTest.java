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

    /** 일간 추이가 더는 읽지 않는 날 이후로는 들고 있지 않는다. */
    @Test
    void 일간_지점은_창을_벗어나는_날까지만_둔다() {
        // 9/8 에 29일 전(8/10) 지점을 계산 — 일간 창은 오늘 포함 30일이라 9/8 이 마지막으로 읽는 날.
        java.time.Duration ttl = CacheTtlPolicy.forHistoryPoint(TODAY.minusDays(29), NOW, settledSheet(), true);
        assertEquals(java.time.Duration.between(NOW, LocalDate.of(2026, 9, 9).atStartOfDay()), ttl);

        // 어제 지점은 아직 29일 남았다 — 30일 상한보다 짧은 쪽.
        java.time.Duration yesterday = CacheTtlPolicy.forHistoryPoint(TODAY.minusDays(1), NOW, settledSheet(), true);
        assertEquals(java.time.Duration.between(NOW, LocalDate.of(2026, 10, 7).atStartOfDay()), yesterday);
    }

    /** 1일 지점은 월간 추이가 계속 읽으므로 창 상한을 받지 않는다. */
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
