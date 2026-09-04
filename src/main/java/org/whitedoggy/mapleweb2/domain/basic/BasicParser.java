package org.whitedoggy.mapleweb2.domain.basic;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

@Component
public class BasicParser {
    public String characterName(JsonNode basic) {
        return Jsons.text(basic, "character_name");
    }
    public String characterWorld(JsonNode basic) {
        return Jsons.text(basic, "world_name");
    }
    public String characterGuild(JsonNode basic) {
        return Jsons.text(basic, "character_guild_name");
    }
    public String characterClass(JsonNode basic) {
        return Jsons.text(basic, "character_class");
    }
    public Integer characterLevel(JsonNode basic) {
        return Jsons.optionalInt(basic, "character_level").orElse(0);
    }
    /**
     * 최근 7일간 접속하지 않은 캐릭터인가.
     *
     * <p>미접속이면 넥슨의 stat 스냅샷이 마지막 접속 시점에 멈춰 있을 수 있다.
     * 그동안 유니온처럼 계정 단위로 자라는 요소는 계속 반영되므로 계산만 앞서간다.
     * 실측: 미접속 189명 중 8명 어긋남(4.23%), 접속 4,918명 중 13명(0.26%).
     */
    public boolean isInactive(JsonNode basic) {
        return "false".equals(Jsons.text(basic, "access_flag"));
    }

    public String characterImage(JsonNode basic) {
        return Jsons.text(basic, "character_image");
    }
}
