package org.whitedoggy.mapleweb2.domain.item.data;

/**
 * 게임 아이템 창의 스탯 한 줄. {@code STR +93 (5 +79 +9)} 처럼 총합과 내역을 함께 보여준다.
 *
 * <p>계산에는 쓰지 않는다 — 계산은 {@code item_total_option} 을 문구로 바꿔 파싱한 시트로 한다.
 * 여기 담는 것은 "이 장비가 어떻게 그 값이 됐나"를 사람이 읽을 수 있게 하려는 것뿐이다.
 *
 * @param percent 퍼센트 값인가. 보스 데미지·방어율 무시·올스탯·데미지가 그렇다.
 */
public record ItemStatLine(
        String name,
        int total,
        int base,
        int add,
        int scroll,
        int starforce,
        boolean percent
) {
}
