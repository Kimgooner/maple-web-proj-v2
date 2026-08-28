package org.whitedoggy.mapleweb2.domain.union.raider;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 유니온 공격대 응답 파서.
 *
 * <p>넥슨이 유니온 API 스키마를 개편해 {@code union_raider_preset_N} / {@code union_occupied_stat} /
 * {@code union_block}이 모두 비었고(픽스처 105건 전수 확인), 다음 필드로 옮겨졌다:
 * <ul>
 *   <li>{@code union_raider_stat} — 공격대원 효과. <b>최상위에만 있고 프리셋 구분이 없다.</b></li>
 *   <li>{@code union_state_stat} — 현재 적용 중인 점령 효과</li>
 *   <li>{@code union_state_stat_preset[]} — 프리셋별 점령 효과 ({@code preset_no}로 매칭)</li>
 * </ul>
 *
 * <p>따라서 프리셋 선택이 의미를 갖는 것은 <b>점령 효과뿐</b>이다. 공격대원 효과는
 * 프리셋과 무관하게 현재 값 하나만 존재한다.
 */
@Component
public class RaiderParser {

    /**
     * 유니온 스탯 프리셋 개수.
     *
     * <p>2026년 7월 업데이트에서 5개 → 10개로 확장됐다.
     * 출처: <a href="https://maplestory.nexon.com/news/update/808">공식 업데이트 공지</a>
     * — "유니온 스탯 프리셋 개수가 10개로 확장됩니다."
     */
    private static final int MAX_PRESET_NO = 10;

    /** 공격대원 효과. 프리셋 구분이 없으므로 항상 현재 값이다. */
    public List<String> getUnionRaiderStat(JsonNode raider) {
        return readTextArray(raider.path("union_raider_stat"));
    }

    /** 현재 적용 중인 점령 효과. */
    public List<String> getUnionOccupiedStat(JsonNode raider) {
        return readTextArray(raider.path("union_state_stat"));
    }

    /**
     * 공격대 데이터가 실려 왔는가.
     *
     * <p>2026년 7월 유니온 개편 이후 <b>게임에 접속하지 않은 캐릭터</b>는 공격대원·점령 효과가
     * 빈 값으로 온다. 유니온이 없는 것과는 다르다 — 챔피언·아티팩트는 그대로 남아 있다.
     */
    public boolean hasRaiderData(JsonNode raider) {
        return !getUnionRaiderStat(raider).isEmpty() || !getUnionOccupiedStat(raider).isEmpty();
    }

    public Integer getUnionCurrentUse(JsonNode raider) {
        return Jsons.optionalInt(raider, "use_preset_no").orElse(1);
    }

    /**
     * 공격대원 효과에는 프리셋이 없다. 파라미터는 호출부 호환을 위해 남겨두고 무시한다.
     */
    public List<String> getUnionRaiderStatByPreset(JsonNode raider, int presetNo) {
        return getUnionRaiderStat(raider);
    }

    /** 프리셋별 점령 효과. 해당 프리셋이 없으면 현재 적용값으로 떨어진다. */
    public List<String> getUnionOccupiedStatByPreset(JsonNode raider, int presetNo) {
        JsonNode preset = findStatPreset(raider, presetNo);
        if (preset.isMissingNode()) {
            return getUnionOccupiedStat(raider);
        }
        return readTextArray(preset.path("union_state_stat"));
    }

    public List<String> getCombatStatEffectsByPreset(JsonNode raider, int presetNo) {
        List<String> effects = new ArrayList<>();
        effects.addAll(getUnionRaiderStatByPreset(raider, presetNo));
        effects.addAll(getUnionOccupiedStatByPreset(raider, presetNo));
        return effects;
    }

    public List<Integer> availablePresets(JsonNode raider) {
        List<Integer> presets = new ArrayList<>();
        for (int presetNo = 1; presetNo <= MAX_PRESET_NO; presetNo++) {
            if (!findStatPreset(raider, presetNo).isMissingNode()) {
                presets.add(presetNo);
            }
        }
        if (presets.isEmpty()) {
            presets.add(getUnionCurrentUse(raider));
        }
        return presets;
    }

    public int scorePreset(JsonNode raider, int presetNo) {
        int score = 0;
        for (String stat : getUnionOccupiedStatByPreset(raider, presetNo)) {
            if (stat.contains("크리티컬 확률")) {
                score += 30;
            }
            if (stat.contains("크리티컬 데미지")) {
                score += 35;
            }
            if (stat.contains("보스 몬스터 공격 시 데미지")) {
                score += 40;
            }
            if (stat.contains("방어율 무시")) {
                score += 30;
            }
            if (stat.contains("공격력")) {
                score += 20;
            }
        }
        return score;
    }

    private JsonNode findStatPreset(JsonNode raider, int presetNo) {
        for (JsonNode preset : raider.path("union_state_stat_preset")) {
            if (Jsons.optionalInt(preset, "preset_no").orElse(-1) == presetNo) {
                return preset;
            }
        }
        return tools.jackson.databind.node.MissingNode.getInstance();
    }

    private List<String> readTextArray(JsonNode array) {
        List<String> list = new ArrayList<>();
        for (JsonNode node : array) {
            EffectTextSplitter.addSplit(list, node.asText());
        }
        return list;
    }
}
