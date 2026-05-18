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
        return Jsons.text(basic, "character_world");
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
    public String characterImage(JsonNode basic) {
        return Jsons.text(basic, "character_image");
    }
}
