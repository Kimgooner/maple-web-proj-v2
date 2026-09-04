package org.whitedoggy.mapleweb2.domain.basic;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

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

    /**
     * 캐릭터 생성일(KST). 조회 구간이 생성 이전으로 내려가는지 판단할 때 쓴다.
     *
     * <p>생성 이전 날짜를 조회하면 API 는 200 을 주면서 모든 필드를 null 로 채운다.
     * 그 응답만 보고 끊으면 "전일 데이터가 아직 안 열린 것"과 구분되지 않으므로
     * (전일치는 다음날 02:00 KST 부터 열린다) 생성일로 미리 자르는 쪽이 안전하다.
     *
     * @return 생성일. 값이 없거나 형식이 어긋나면 null.
     */
    public LocalDate characterCreatedAt(JsonNode basic) {
        String raw = Jsons.text(basic, "character_date_create");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(KST).toLocalDate();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
}
