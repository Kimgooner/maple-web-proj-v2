package org.whitedoggy.mapleweb2.domain.union.champion;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 유니온 챔피언 배지 효과 파서.
 *
 * <p><b>배지는 챔피언으로 등록된 캐릭터에게만 붙는다.</b> 같은 유니온이어도
 * 챔피언이 아닌 캐릭터는 이 효과를 받지 않는다. API는 계정의 배지 총합을
 * {@code champion_badge_total_info}로 주기 때문에 그대로 쓰면 챔피언이 아닌
 * 캐릭터까지 받게 된다.
 *
 * <p>랭킹 표본 659명에서 확인했다. 이 조건을 넣으면 정수 일치가 563명에서
 * 582명이 되고, 반대로 틀어지는 캐릭터는 없다.
 *
 * <p>{@code union_champion} 목록이 비어 있으면(배지 총합만 오는 경우가 있다)
 * 판단할 근거가 없으므로 그대로 적용한다 — 그 6명은 적용이 맞았다.
 */
@Component
public class ChampionParser {
    public List<String> getChampionStats(JsonNode champion, String characterName) {
        if (!isChampion(champion, characterName)) {
            return List.of();
        }
        List<String> effects = new ArrayList<>();
        for (JsonNode effect : champion.path("champion_badge_total_info")) {
            EffectTextSplitter.addSplit(effects, Jsons.text(effect, "stat"));
        }
        return effects;
    }

    /** 챔피언 목록에 이 캐릭터가 있는가. 목록 자체가 없으면 판단하지 않고 참으로 본다. */
    private boolean isChampion(JsonNode champion, String characterName) {
        JsonNode list = champion.path("union_champion");
        if (!list.isArray() || list.isEmpty()) {
            return true;
        }
        for (JsonNode entry : list) {
            if (Jsons.text(entry, "champion_name").equals(characterName)) {
                return true;
            }
        }
        return false;
    }
}
