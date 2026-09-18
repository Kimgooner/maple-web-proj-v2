package org.whitedoggy.mapleweb2.analysis.support;

import org.whitedoggy.mapleweb2.analysis.data.DataSheet;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 데이터시트를 얼마나 오래 캐시에 둘지 정한다.
 *
 * <p>기준은 하나다 — <b>이 값이 다시 받으면 달라질 수 있는가.</b> 넥슨은 {@code date} 를 붙인
 * 과거 조회에 언제 물어도 같은 값을 준다. 그래서 지나간 날짜의 온전한 시트는 길게 둔다.
 * 반대로 아직 열리지 않은 시점이나 문서가 빠진 채로 계산된 시트는 곧 달라지므로 짧게 둔다.
 */
public final class CacheTtlPolicy {

    /** 오늘치는 캐릭터가 움직이는 대로 계속 변한다. */
    public static final Duration TODAY = Duration.ofHours(6);

    /** 굳은 과거. 스냅샷 한 지점이 넥슨 호출 15회라 다시 받을 이유가 없다. */
    public static final Duration SETTLED_PAST = Duration.ofDays(30);

    /** 아직 굳지 않은 값. 곧 다시 받는다. */
    public static final Duration UNSETTLED = Duration.ofMinutes(30);

    /**
     * 전일 데이터가 열리는 시각(KST). 그전에 어제를 조회하면 빈 문서가 오고,
     * 그것을 길게 캐시하면 하루 종일 빈 값이 굳는다.
     */
    private static final LocalTime PREVIOUS_DAY_OPENS_AT = LocalTime.of(2, 0);

    private CacheTtlPolicy() {
    }

    /**
     * @param date   시트가 가리키는 날짜
     * @param nowKst 지금 (Asia/Seoul)
     * @param sheet  방금 만든 시트
     */
    public static Duration forDataSheet(LocalDate date, LocalDateTime nowKst, DataSheet sheet) {
        LocalDate today = nowKst.toLocalDate();
        if (!date.isBefore(today)) {
            return TODAY;
        }
        if (date.equals(today.minusDays(1)) && nowKst.toLocalTime().isBefore(PREVIOUS_DAY_OPENS_AT)) {
            return UNSETTLED;
        }
        return isSettled(sheet) ? SETTLED_PAST : UNSETTLED;
    }

    /** 일간 추이가 담는 날 수. {@code HistoryRange.DAILY} 와 같다 — 오늘 포함 30일. */
    private static final int DAILY_WINDOW_DAYS = 30;

    /**
     * 추이 지점용. 시트 기준으로 정한 뒤, 헥사 문서를 못 받았으면 짧은 쪽으로 내린다 —
     * 조각이 빠진 지점을 30일 들고 있으면 그 캐릭터 그래프에 구멍이 굳는다.
     *
     * <p>그리고 <b>다시 읽힐 수 없는 날까지만</b> 둔다. 일간 추이는 오늘 포함 30일이라, 매달 1일이
     * 아닌 날짜의 지점은 그 날짜로부터 30일이 지나면 어떤 조회도 읽지 않는다. 30일 전 지점을 오늘
     * 계산해 놓고 다시 30일을 들고 있으면 그중 29일은 아무도 안 읽는 값이다 — 콜드 조회 한 번이
     * 만드는 30점 중 절반이 그렇다. 1일 지점은 월간 추이가 12달 동안 읽으므로 그대로 30일이다.
     */
    public static Duration forHistoryPoint(
            LocalDate date, LocalDateTime nowKst, DataSheet sheet, boolean hexaLoaded) {
        Duration ttl = forDataSheet(date, nowKst, sheet);
        if (!hexaLoaded && ttl.compareTo(UNSETTLED) > 0) {
            ttl = UNSETTLED;
        }
        return min(ttl, untilLeavesDailyWindow(date, nowKst));
    }

    /** 이 지점을 일간 추이가 마지막으로 읽는 날의 자정까지. 1일 지점은 상한이 없다. */
    private static Duration untilLeavesDailyWindow(LocalDate date, LocalDateTime nowKst) {
        if (date.getDayOfMonth() == 1) {
            return SETTLED_PAST;
        }
        Duration left = Duration.between(nowKst, date.plusDays(DAILY_WINDOW_DAYS).atStartOfDay());
        // 이미 창 밖인 지점(프리셋 되돌리기 등으로 계산될 수 있다)은 짧게만 둔다.
        return left.compareTo(UNSETTLED) < 0 ? UNSETTLED : left;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * 다시 받아도 같은 값이 나올 시트인가.
     *
     * <p>{@code weaponMissing} 을 결손으로 보는 것은, 장비 문서가 빈 채로 200 이 오면
     * 문서가 빠진 티가 안 나면서 무기만 사라지기 때문이다. 무기를 정말로 안 낀 캐릭터도
     * 여기 걸리지만, 그 전투력 값은 어차피 쓸 수 없는 값이다.
     */
    private static boolean isSettled(DataSheet sheet) {
        return sheet != null
                && sheet.getCombatPower() != null
                && !sheet.isIncompleteSnapshot()
                && !sheet.isWeaponMissing();
    }
}
