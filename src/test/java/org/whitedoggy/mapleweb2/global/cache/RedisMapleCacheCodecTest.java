package org.whitedoggy.mapleweb2.global.cache;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMapleCacheCodecTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 작은_값은_그대로_JSON_이다() throws Exception {
        byte[] bytes = RedisMapleCache.encode(mapper, Map.of("date", "2026-09-18", "combatPower", 1));
        assertThat(new String(bytes)).startsWith("{");
        assertThat(RedisMapleCache.decode(bytes)).isEqualTo(bytes);
    }

    /** 데이터시트처럼 큰 값은 gzip 으로 눌리고, 풀면 같은 JSON 이다 — 버리는 데이터가 없다. */
    @Test
    void 큰_값은_gzip_으로_눌리고_풀면_같다() throws Exception {
        // 데이터시트처럼 같은 필드 이름이 부위마다 되풀이되는 모양.
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(Map.of("name", "아케인셰이드 부위 " + i, "STR", 239 + i, "DEX", 239, "HP", 13195,
                    "ATTACK_POWER_PERCENT", 0.0, "MAGIC_POWER_PERCENT", 0.0, "lines", List.of("최대 HP +13%", "공격력 +12%")));
        }
        Map<String, Object> big = Map.of("items", items);
        byte[] bytes = RedisMapleCache.encode(mapper, big);
        byte[] json = mapper.writeValueAsBytes(big);
        assertThat(json.length).isGreaterThanOrEqualTo(RedisMapleCache.COMPRESS_FROM_BYTES);
        assertThat(bytes[0] & 0xff).isEqualTo(0x1f);
        assertThat(bytes.length).isLessThan(json.length);
        assertThat(RedisMapleCache.decode(bytes)).isEqualTo(json);
    }

    /** 누르기 전에 써 둔 값(순수 JSON)도 그대로 읽힌다. */
    @Test
    void 옛_JSON_값도_읽는다() throws Exception {
        byte[] legacy = "{\"version\":\"aaa\",\"value\":{\"x\":1}}".getBytes();
        assertThat(RedisMapleCache.decode(legacy)).isEqualTo(legacy);
    }
}
