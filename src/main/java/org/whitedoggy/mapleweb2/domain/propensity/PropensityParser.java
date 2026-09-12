package org.whitedoggy.mapleweb2.domain.propensity;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

/**
 * 성향(propensity). 전투력에 들어가는 것은 의지의 최대 HP 뿐이고, 그것도 HP 가 주스탯인
 * 데몬어벤져에게만 뜻이 있다 — 다른 직업의 HP 는 전투력에 없다.
 *
 * <p>카리스마(방어율 무시)·통찰력·손재주·감성·매력은 전투력에 들어가지 않는다.
 */
@Component
public class PropensityParser {

    /** 의지 레벨. 문서가 없거나(옛 픽스처) 비어 있으면 0. */
    public int willpowerLevel(JsonNode propensity) {
        if (propensity == null || propensity.isMissingNode() || propensity.isNull()) {
            return 0;
        }
        return Jsons.optionalInt(propensity, "willingness_level").orElse(0);
    }
}
