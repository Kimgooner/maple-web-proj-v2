package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.skill.ChallengersBuffs;
import org.whitedoggy.mapleweb2.domain.skill.SkillRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 설정 바인딩 가드.
 *
 * <p>Map 키가 한글이면 {@code "[챌린저스]"}처럼 대괄호로 감싸야 한다. 그렇지 않으면 Spring의
 * relaxed binding이 키를 0,1,2… 인덱스로 바꿔버리는데, 예외 없이 조용히 빈 값이 되어
 * 전투력만 낮아진다. 실제로 한 번 겪은 함정이라 테스트로 막는다.
 */
@SpringBootTest
class SkillRulesBindingTest {

    @Autowired
    private SkillRules skillRules;

    @Autowired
    private ChallengersBuffs challengersBuffs;

    @Autowired
    private GameData gameData;

    @Test
    void 이벤트_버프_설정이_바인딩된다() {
        assertTrue(skillRules.directSkillNames().contains("훈련 일지"), "event-buffs.yml 목록이 비었다");
    }

    @Test
    void 챌린저스_설정이_바인딩된다() {
        assertFalse(challengersBuffs.effectsOf("챌린저스").isEmpty(),
                "challengers-buffs.yml의 Map 키를 \"[챌린저스]\" 형태로 감쌌는지 확인할 것");
        assertEquals(7, challengersBuffs.tierEffects().size(), "티어 7종이 모두 등록돼야 한다");
    }

    @Test
    void 챌린저스_버프는_챌린저스_월드에만_적용된다() {
        assertTrue(challengersBuffs.appliesTo("챌린저스"));
        assertTrue(challengersBuffs.appliesTo("챌린저스3"));
        assertFalse(challengersBuffs.appliesTo("스카니아"));
        assertFalse(challengersBuffs.appliesTo(null));
    }

    @Test
    void 게임_데이터가_바인딩된다() {
        assertEquals(48, gameData.jobNames().size(), "직업 48종이 모두 등록돼야 한다");
        assertTrue(gameData.jobNames().contains("캐논마스터"), "API 실제 값은 캐논마스터다");
        assertTrue(gameData.isMagicWeaponPart("카르타"));
    }
}
