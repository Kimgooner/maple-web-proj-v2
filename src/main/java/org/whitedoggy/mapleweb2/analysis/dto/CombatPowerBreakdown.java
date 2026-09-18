package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

/**
 * 전투력이 어떻게 나왔는지. 식의 항 하나가 필드 하나다.
 *
 * <pre>전투력 = floor( 스탯항 × 공격력(마력) × 데미지 × 크리티컬 데미지 × 최종 데미지 / 1,000,000 × 보정 )</pre>
 *
 * <p>화면이 캐릭터 시트를 펼쳤을 때 이 식을 그대로 보여 준다. 값은 종합 시트에서 계산이
 * 실제로 쓴 것이라, 여기 적힌 수를 곱하면 {@code combatPower} 가 나온다.
 *
 * @param mainStat          최종 주스탯. 데몬어벤져는 HP 를 환산한 값이라 소수일 수 있다.
 *                          제논은 세 스탯 합에 직업 계수를 곱한 값이다.
 * @param subStat           최종 부스탯. 부스탯이 둘이면 합, 제논은 0.
 * @param statTerm          {@code (주스탯 × 4 + 부스탯) / 100}
 * @param usesMagic         마력을 쓰는 직업인가. {@code power} 가 공격력인지 마력인지.
 * @param power             공격력(마력)에 %를 곱해 내린 값
 * @param damage            데미지%. 식에는 100 + 데미지 + 보공 으로 들어간다
 * @param bossDamage        보스 몬스터 데미지%
 * @param criticalDamage    크리티컬 데미지%. 식에는 135 + 크뎀 으로 들어간다
 * @param finalDamage       최종 데미지 배수(%). 시트의 최종 데미지에 설명문에만 있는 장비(루인
 *                          포스실드)를 곱해 놓은 값이라, 식에는 100 + 이 값이 들어간다
 * @param correction        직업 보정 상수. 데몬어벤져·제논처럼 스탯 계산이 다른 직업만 있고 나머지는 null
 * @param combatPower       위 항을 곱해 내린 전투력
 * @param sources           종합에 들어간 소스별 스탯 합과 전투력 몫. 종합 차례({@code DataSheet.SOURCE_ORDER})다
 * @param total             종합 시트. 소스를 전부 더한 것이고 위 항들은 여기서 나온다
 */
public record CombatPowerBreakdown(
        double mainStat,
        double subStat,
        double statTerm,
        boolean usesMagic,
        double power,
        double damage,
        double bossDamage,
        double criticalDamage,
        double finalDamage,
        Double correction,
        long combatPower,
        List<SourceShare> sources,
        StatSheetSummary total
) {
    /**
     * 소스 하나의 몫.
     *
     * @param source       시트 이름 (items, setEffect, skill …)
     * @param stats        이 소스가 종합에 더한 스탯
     * @param sharePercent 전투력 구성 비율(%). 섀플리 값이라 소스를 다 더하면 100 이다
     */
    public record SourceShare(String source, StatSheetSummary stats, double sharePercent) {
    }
}
