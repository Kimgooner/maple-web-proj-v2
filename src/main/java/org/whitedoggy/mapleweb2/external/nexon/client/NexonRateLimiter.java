package org.whitedoggy.mapleweb2.external.nexon.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NexonRateLimiter {
    private final long intervalNanos;
    private final AtomicLong nextPermitAtNanos = new AtomicLong(System.nanoTime());

    public NexonRateLimiter(@Value("${nexon.api.requests-per-second:300}") int requestsPerSecond) {
        this.intervalNanos = requestsPerSecond <= 0
                ? 0L
                : Math.max(1L, 1_000_000_000L / requestsPerSecond);
    }

    public Mono<Void> acquire() {
        if (intervalNanos == 0L) {
            return Mono.empty();
        }

        long delayNanos = reserveDelayNanos();
        if (delayNanos <= 0L) {
            return Mono.empty();
        }
        return Mono.delay(Duration.ofNanos(delayNanos)).then();
    }

    private long reserveDelayNanos() {
        long now = System.nanoTime();
        while (true) {
            long current = nextPermitAtNanos.get();
            long permitAt = Math.max(current, now);
            long nextPermitAt = permitAt + intervalNanos;
            if (nextPermitAtNanos.compareAndSet(current, nextPermitAt)) {
                return permitAt - now;
            }
        }
    }
}
