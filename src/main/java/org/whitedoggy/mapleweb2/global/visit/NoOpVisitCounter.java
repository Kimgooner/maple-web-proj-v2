package org.whitedoggy.mapleweb2.global.visit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Redis 를 안 쓰는 로컬·테스트에서는 세지 않는다. */
@Component
@ConditionalOnProperty(name = "maple.cache.type", havingValue = "memory", matchIfMissing = true)
public class NoOpVisitCounter implements VisitCounter {
    @Override
    public Mono<Void> record(String characterName, String clientKey) {
        return Mono.empty();
    }
}
