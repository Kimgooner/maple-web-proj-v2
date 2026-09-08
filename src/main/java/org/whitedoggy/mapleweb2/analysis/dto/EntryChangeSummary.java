package org.whitedoggy.mapleweb2.analysis.dto;

/** 이름 있는 항목(스킬·심볼·세트…) 하나의 변화. 없던 것은 previous 가 null, 사라진 것은 current 가 null. */
public record EntryChangeSummary(
        String name,
        String previous,
        String current,
        /** 넥슨 아이콘 URL. 스킬·심볼만 있고 나머지는 null */
        String icon,
        /** 펼쳐 봤을 때 보여줄 여러 줄 설명. 세트 효과 문구 등. 없으면 null */
        String detail
) {
}
