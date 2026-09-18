package org.whitedoggy.mapleweb2.analysis.history;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 전투력 추이 조회 구간. 날짜는 전부 KST 00:00 기준이다.
 *
 * <p>목록은 <b>최신이 먼저</b>다. 캐릭터가 없는 시점에서 끊어야 하므로
 * 최신에서 과거로 내려가며 훑는다. 세 구간 모두 오른쪽 끝은 오늘이다.
 *
 * <p>지점 하나가 넥슨 호출 16회라 구간의 지점 수가 곧 비용이다. 30일을 매일 찍으면 콜드 조회가
 * 480회였는데, 주간(매일 7점)·월간(3일 격자 ~11점)·연간(1일 12점)으로 나눠 기본 화면(주간)을
 * 96회로 줄였다. 세 구간이 같은 지점 캐시를 나눠 쓴다.
 */
public enum HistoryRange {

    /** 오늘을 포함해 하루 간격 7개. 오늘 ~ 6일 전. */
    WEEKLY(7) {
        @Override
        public List<LocalDate> dates(LocalDate today) {
            return IntStream.range(0, count()).mapToObj(today::minusDays).toList();
        }
    },

    /**
     * 오늘 + 지난 29일 중 3일 격자에 놓인 날. 격자는 날짜 번호(epoch day)가 3의 배수인 날이라
     * 달력에 고정돼 있다 — "오늘부터 3일씩 뒤로"로 잡으면 내일은 전부 하루씩 밀려 어제 만든
     * 지점을 하나도 못 쓴다. 오늘이 격자에 있으면 10개, 아니면 11개.
     */
    MONTHLY(11) {
        @Override
        public List<LocalDate> dates(LocalDate today) {
            List<LocalDate> dates = new ArrayList<>();
            dates.add(today);
            for (int back = 1; back <= MONTHLY_WINDOW_DAYS - 1; back++) {
                LocalDate date = today.minusDays(back);
                if (isOnGrid(date)) {
                    dates.add(date);
                }
            }
            return List.copyOf(dates);
        }
    },

    /**
     * 오른쪽 끝은 오늘, 그 앞은 지난달부터 달마다 1일. 오늘 포함 12개 (오늘 ~ 11개월 전 1일).
     * 오늘이 1일이면 종전과 같은 목록이다.
     */
    YEARLY(12) {
        @Override
        public List<LocalDate> dates(LocalDate today) {
            LocalDate firstOfThisMonth = today.withDayOfMonth(1);
            List<LocalDate> dates = new ArrayList<>();
            dates.add(today);
            for (int back = 1; dates.size() < count(); back++) {
                dates.add(firstOfThisMonth.minusMonths(back));
            }
            return List.copyOf(dates);
        }
    };

    /** 주간이 담는 날 수. 오늘 포함. */
    public static final int WEEKLY_WINDOW_DAYS = 7;
    /** 월간이 담는 날 수. 오늘 포함. */
    public static final int MONTHLY_WINDOW_DAYS = 30;
    private static final int GRID_DAYS = 3;

    private final int count;

    HistoryRange(int count) {
        this.count = count;
    }

    /** 월간 격자에 놓인 날인가. {@code CacheTtlPolicy} 가 지점을 언제까지 둘지 정할 때도 본다. */
    public static boolean isOnGrid(LocalDate date) {
        return Math.floorMod(date.toEpochDay(), GRID_DAYS) == 0;
    }

    /** 이 구간이 보통 요청하는 지점 수. 월간은 오늘이 격자에 있으면 하나 적고, 캐릭터가 없어 끊기면 더 적다. */
    public int count() {
        return count;
    }

    public abstract List<LocalDate> dates(LocalDate today);

    /** 쿼리 파라미터를 enum 으로. 대소문자를 가리지 않는다. 옛 화면이 보내는 {@code daily} 는 주간으로 받는다. */
    public static HistoryRange from(String value) {
        if (value != null) {
            String wanted = value.trim();
            if (wanted.equalsIgnoreCase("daily")) {
                return WEEKLY;
            }
            for (HistoryRange range : values()) {
                if (range.name().equalsIgnoreCase(wanted)) {
                    return range;
                }
            }
        }
        throw new IllegalArgumentException("range 는 weekly, monthly, yearly 중 하나여야 합니다: " + value);
    }
}
