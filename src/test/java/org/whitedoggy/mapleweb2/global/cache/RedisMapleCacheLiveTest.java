package org.whitedoggy.mapleweb2.global.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.SourceEntry;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 진짜 Redis 를 상대로 하는 왕복. 로컬에 Redis 가 없으면 건너뛴다.
 *
 * <pre>docker run -d --name maple-redis -p 6379:6379 redis:7-alpine</pre>
 */
@SpringBootTest(properties = "maple.cache.type=redis")
@EnabledIf("redisIsReachable")
class RedisMapleCacheLiveTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6379;
    private static final String KEY = "maple:test:live:datasheet";

    @Autowired
    private MapleCache cache;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void dataSheetSurvivesARoundTripThroughRedis() {
        redis.delete(KEY).block();
        DataSheet original = sheet();

        StepVerifier.create(cache.put(KEY, original, Duration.ofMinutes(5)))
                .expectNext(original)
                .verifyComplete();

        DataSheet restored = cache.get(KEY, DataSheet.class).block();

        assertEquals(1234567L, restored.getCombatPower());
        assertEquals(100, restored.getSymbol().getSTR());
        assertEquals(12.5, restored.getSymbol().getBOSS_DAMAGE());
        assertEquals("이름", restored.getItemEquip().get("장비 - 무기").getItemName());
        assertEquals("30", restored.getSourceEntries().get("skill").get("스킬").value());

        assertEquals(mapper.writeValueAsString(original), mapper.writeValueAsString(restored),
                "Redis 를 거치고 나서 시트가 달라졌다");

        Long ttlSeconds = redis.getExpire(KEY).block().toSeconds();
        assertTrue(ttlSeconds > 0 && ttlSeconds <= 300, "TTL 이 붙지 않았다: " + ttlSeconds);

        redis.delete(KEY).block();
    }

    /** 포맷이 바뀌어 못 읽는 값은 미스로 두고 지운다. */
    @Test
    void unreadableValueIsTreatedAsAMissAndRemoved() {
        redis.opsForValue().set(KEY, "{\"combatPower\":\"숫자가 아님\"}").block();

        StepVerifier.create(cache.get(KEY, DataSheet.class))
                .verifyComplete();

        assertEquals(Boolean.FALSE, redis.hasKey(KEY).block());
    }

    static boolean redisIsReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static DataSheet sheet() {
        DataSheet sheet = new DataSheet();
        StatSheet symbol = new StatSheet("symbol");
        symbol.setSTR(100);
        symbol.setBOSS_DAMAGE(12.5);
        sheet.setSymbol(symbol);

        ItemSnapShot weapon = new ItemSnapShot("이름", "아이콘");
        weapon.setStatSheet(symbol);
        sheet.setItemEquip(Map.of("장비 - 무기", weapon));
        sheet.setPetEquip(Map.of());
        sheet.setCashEquip(Map.of());
        sheet.setSourceEntries(Map.of("skill", Map.of("스킬", SourceEntry.of("30"))));
        sheet.setCombatPower(1234567L);
        return sheet;
    }
}
