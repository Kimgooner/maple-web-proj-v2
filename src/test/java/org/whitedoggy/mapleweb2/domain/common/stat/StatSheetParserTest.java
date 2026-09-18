package org.whitedoggy.mapleweb2.domain.common.stat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StatSheetParserTest {

    @Autowired
    private StatSheetParser parser;

    /**
     * 소울 잠재(2026-09-17)는 "공격력 +4.5%", "LUK +0.5%" 처럼 소수 % 를 준다.
     * 정수로 잘라 4% 로 넣으면 09-17 표본에서 소울 잠재 보유자 5명이 0.2% 씩 어긋났다.
     */
    @Test
    void 소수_퍼센트를_잘라내지_않는다() {
        StatSheet sheet = parser.parse(List.of("공격력 +4.5%", "마력 +1.5%", "LUK +0.5%", "올스탯 +1.5%", "최대 HP +2.5%"));
        assertThat(sheet.getATTACK_POWER_PERCENT()).isEqualTo(4.5);
        assertThat(sheet.getMAGIC_POWER_PERCENT()).isEqualTo(1.5);
        assertThat(sheet.getLUK_PERCENT()).isEqualTo(0.5);
        assertThat(sheet.getALL_STAT_PERCENT()).isEqualTo(1.5);
        assertThat(sheet.getHP_PERCENT()).isEqualTo(2.5);
    }

    /** 고정값은 전과 같이 정수다. */
    @Test
    void 고정값은_정수로_읽는다() {
        StatSheet sheet = parser.parse(List.of("공격력 +20", "LUK +2", "최대 HP +10"));
        assertThat(sheet.getATTACK_POWER()).isEqualTo(20);
        assertThat(sheet.getLUK()).isEqualTo(2);
        assertThat(sheet.getHP()).isEqualTo(10);
        assertThat(sheet.getATTACK_POWER_PERCENT()).isEqualTo(0.0);
    }
}
