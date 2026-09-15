package org.whitedoggy.mapleweb2.analysis.data;

/**
 * 핵심 소스의 이름 있는 항목 하나. 값은 사람이 읽는 문자열(레벨, 세트 수, 파싱된 스탯 요약),
 * 아이콘은 넥슨이 주는 URL 이며 없으면 null.
 *
 * @param detail 펼쳐 봤을 때만 보여줄 여러 줄 설명. 세트 효과 문구처럼 값 한 줄로는 못 담는 것.
 *               변화 판정은 {@code value} 로만 하므로 여기 무엇이 들어와도 없던 변화가 잡히지 않는다.
 * @param badge  이름 밑에 블럭으로 붙일 짧은 말. 헥사 코어의 종류가 그렇다 - 이름이 긴 것이 있어
 *               옆에 붙이면 줄이 밀리고, 값 쪽에 두면 이전·이후에 같은 말이 두 번 적힌다.
 * @param spent  헥사 코어에 지금까지 부은 솔 에르다 조각. 값 문자열에 섞지 않는 것은, 화면이
 *               이전·이후 누적을 나란히 적는 대신 그 차이만 블럭으로 붙이기 때문이다. 없으면 null.
 */
public record SourceEntry(String value, String icon, String detail, String badge, Long spent) {
    public SourceEntry(String value, String icon) {
        this(value, icon, null, null, null);
    }

    public SourceEntry(String value, String icon, String detail) {
        this(value, icon, detail, null, null);
    }

    public SourceEntry(String value, String icon, String detail, String badge) {
        this(value, icon, detail, badge, null);
    }

    public static SourceEntry of(String value) {
        return new SourceEntry(value, null, null, null, null);
    }
}
