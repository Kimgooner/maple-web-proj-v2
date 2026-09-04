package org.whitedoggy.mapleweb2.analysis.history;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 전투력 추이 조회 구간. 날짜는 전부 KST 00:00 기준이다.
 *
 * <p>목록은 <b>최신이 먼저</b>다. 캐릭터가 없는 시점에서 끊어야 하므로
 * 최신에서 과거로 내려가며 훑는다.
 */
public enum HistoryRange {

    /** 오늘을 포함해 하루 간격 30개. 오늘 ~ 29일 전. */
    DAILY(30) {
        @Override
        public List<LocalDate> dates(LocalDate today) {
            return IntStream.range(0, count()).mapToObj(today::minusDays).toList();
        }
    },

    /** 오늘이 속한 달을 포함해 달마다 1개, 각 달 1일로 12개. 이번 달 1일 ~ 11개월 전 1일. */
    MONTHLY(12) {
        @Override
        public List<LocalDate> dates(LocalDate today) {
            LocalDate firstOfThisMonth = today.withDayOfMonth(1);
            return IntStream.range(0, count()).mapToObj(firstOfThisMonth::minusMonths).toList();
        }
    };

    private final int count;

    HistoryRange(int count) {
        this.count = count;
    }

    /** 이 구간이 요청하는 지점 수. 캐릭터가 없어 끊기면 실제 결과는 이보다 적다. */
    public int count() {
        return count;
    }

    public abstract List<LocalDate> dates(LocalDate today);

    /** 쿼리 파라미터를 enum 으로. 대소문자를 가리지 않는다. */
    public static HistoryRange from(String value) {
        if (value != null) {
            for (HistoryRange range : values()) {
                if (range.name().equalsIgnoreCase(value.trim())) {
                    return range;
                }
            }
        }
        throw new IllegalArgumentException("range 는 daily 또는 monthly 여야 합니다: " + value);
    }
}
