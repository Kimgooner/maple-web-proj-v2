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
}
