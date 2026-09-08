package org.whitedoggy.mapleweb2.global.cache;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Redis 가 없어도 기동하고, 캐시 호출이 예외 대신 미스로 떨어지는지 본다.
 * 포트는 아무도 듣지 않는 곳을 가리켜 연결이 곧바로 거절되게 한다.
 */
@SpringBootTest(properties = {
        "maple.cache.type=redis",
        "spring.data.redis.port=6399"
})
class RedisMapleCacheDownTest {

    private static final String KEY = "maple:test:redis-down";

    @Autowired
    private MapleCache cache;

    @Test
    void redisImplementationIsSelected() {
        assertInstanceOf(RedisMapleCache.class, cache);
    }

    /**
     * 읽기·쓰기 모두 요청 경로로 예외를 올리지 않는다. 경고는 도배되지 않게
     * 한 번만 남는다 — 조회 하나가 캐시를 수십 번 두드리기 때문이다.
     */
    @Test
    void unreachableRedisBehavesLikeAMissAndWarnsOnce() {
        ListAppender<ILoggingEvent> logs = attachAppender();

        StepVerifier.create(cache.get(KEY, String.class))
                .verifyComplete();
        StepVerifier.create(cache.put(KEY, "값", Duration.ofMinutes(1)))
                .expectNext("값")
                .verifyComplete();
        StepVerifier.create(cache.get(KEY, String.class))
                .verifyComplete();

        assertEquals(1, logs.list.stream().filter(event -> event.getLevel() == Level.WARN).count(),
                "Redis 장애 경고는 1분에 한 번만 남아야 한다: " + logs.list);
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(RedisMapleCache.class)).addAppender(appender);
        return appender;
    }
}
