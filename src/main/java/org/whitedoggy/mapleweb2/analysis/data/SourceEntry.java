package org.whitedoggy.mapleweb2.analysis.data;

/**
 * 핵심 소스의 이름 있는 항목 하나. 값은 사람이 읽는 문자열(레벨, 세트 수, 파싱된 스탯 요약),
 * 아이콘은 넥슨이 주는 URL 이며 없으면 null.
 */
public record SourceEntry(String value, String icon) {
    public static SourceEntry of(String value) {
        return new SourceEntry(value, null);
    }
}
