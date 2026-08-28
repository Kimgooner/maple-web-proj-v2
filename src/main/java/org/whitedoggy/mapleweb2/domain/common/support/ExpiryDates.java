package org.whitedoggy.mapleweb2.domain.common.support;

import java.time.LocalDate;

/**
 * 넥슨 API의 만료 필드 해석.
 *
 * <p>같은 뜻을 세 가지 표기로 준다. 한 곳에서 해석해 파서마다 다르게 판단하는 일이 없게 한다.
 * <ul>
 *   <li>{@code null} / 빈 문자열 — 만료 없음(영구)</li>
 *   <li>{@code "-1"} — 해당 슬롯이 비어 있음. 펫 장비에서 쓰인다.</li>
 *   <li>{@code "expired"} — 이미 만료됨</li>
 *   <li>ISO 8601 일시 — 기준일보다 이전이면 만료</li>
 * </ul>
 */
public final class ExpiryDates {

    private static final String EXPIRED = "expired";
    private static final String EMPTY_SLOT = "-1";

    private ExpiryDates() {
    }

    public static boolean isExpired(String value, LocalDate referenceDate) {
        if (value == null || value.isBlank() || EMPTY_SLOT.equals(value)) {
            return false;
        }
        if (EXPIRED.equals(value)) {
            return true;
        }
        if (referenceDate == null || value.length() < 10) {
            return false;
        }
        try {
            return LocalDate.parse(value.substring(0, 10)).isBefore(referenceDate);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 만료 필드 중 하나라도 지났으면 만료로 본다(본체 기간 / 옵션 기간). */
    public static boolean isAnyExpired(LocalDate referenceDate, String... values) {
        for (String value : values) {
            if (isExpired(value, referenceDate)) {
                return true;
            }
        }
        return false;
    }
}
