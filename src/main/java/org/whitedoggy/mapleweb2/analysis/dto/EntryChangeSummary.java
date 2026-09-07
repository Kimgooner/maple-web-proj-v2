package org.whitedoggy.mapleweb2.analysis.dto;

/** 이름 있는 항목(스킬·심볼·세트…) 하나의 변화. 없던 것은 previous 가 null, 사라진 것은 current 가 null. */
public record EntryChangeSummary(
        String name,
        String previous,
        String current
) {
}
