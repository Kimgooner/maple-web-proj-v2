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
 * <p>{@code union_champion} 목록이 비어 있으면(배지 총합만 오는 경우가 있다) 판단할 근거가
 * 없으므로 그대로 적용한다. 표본 700명 중 11명이 이랬고, 그 11명은 넥슨 전투력과 정수까지
 * 맞았다 — 비었을 때 적용하는 쪽이 맞다.
 *
 * <p><b>다만 명단이 비는 것은 계정 성질이 아니라 그날그날의 결손이다.</b> 그 11명은 모두
 * 다른 날짜에는 자기 명단에 들어 있었고, 명단이 영영 비는 계정은 하나도 없었다. 그래서
 * 챔피언이 아닌 캐릭터가 결손된 날 하루만 배지를 받아 전투력이 솟는 일이 생긴다.
 * {@code SnapshotService} 가 그 날짜의 명단을 현재 명단으로 채워 넣어 이 구멍을 막는다 —
 * 여기 규칙은 그대로 두고, 들어오는 문서 쪽을 고친다.
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

    /**
     * 배지를 적용하긴 했는데 명단이 없어 맞는지 확인하지 못한 상태인가.
     *
     * <p>넥슨이 명단만 비워 보내는 날이 있다. 그날은 챔피언이 아닌 캐릭터도 배지를 받아
     * 전투력이 하루만 솟았다가 다음 날 돌아온다. 계산은 그대로 두고(→ 위 주석의 6명)
     * 화면이 "이 값은 확인이 안 됐다"고 말할 수 있게 여기서 알려 준다.
     */
    public boolean badgeUnverified(JsonNode champion) {
        if (champion == null) {
            return false;
        }
        JsonNode list = champion.path("union_champion");
        boolean listMissing = !list.isArray() || list.isEmpty();
        return listMissing && !champion.path("champion_badge_total_info").isEmpty();
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
