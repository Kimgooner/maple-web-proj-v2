package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.AnalysisDates;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DateService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public AnalysisDates getDates(String dateType) {
        LocalDate today = LocalDate.now(KST);
        return switch (dateType) {
            case "monthly" -> new AnalysisDates(today, getMonthlyDates(today));
            case "yearly" -> new AnalysisDates(today, getYearlyDates(today));
            default -> throw new IllegalArgumentException("지원하지 않는 dateType입니다: " + dateType);
        };
    }

    private List<LocalDate> getMonthlyDates(LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = today.minusDays(daysToPreviousThreeNPlusOne(today));

        while (dates.size() < 9) {
            dates.add(current);
            current = current.minusDays(daysToPreviousThreeNPlusOne(current));
        }

        return dates;
    }

    private int daysToPreviousThreeNPlusOne(LocalDate date) {
        int day = date.getDayOfMonth();
        int remainder = Math.floorMod(day - 1, 3);

        if (remainder == 0) {
            return 3;
        }
        return remainder;
    }

    private List<LocalDate> getYearlyDates(LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();

        for (int monthOffset = 1; dates.size() < 11; monthOffset++) {
            LocalDate month = today.minusMonths(monthOffset);
            dates.add(month.withDayOfMonth(Math.min(15, month.lengthOfMonth())));
        }

        return dates;
    }
}
