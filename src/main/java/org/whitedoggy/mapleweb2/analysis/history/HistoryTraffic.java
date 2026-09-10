package org.whitedoggy.mapleweb2.analysis.history;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 지금 몇 명이 동시에 추이를 받고 있는지.
 *
 * <p>콜드 조회 하나가 넥슨 API 를 510회 쓰는데 초당 호출에 상한이 있어, 동시에 보는 사람이
 * 많으면 다 같이 느려진다. 서버가 멈춘 것이 아니라 줄을 선 것인데, 화면에서는 둘이 똑같이
 * 보인다 - 그래서 기다림이 길어질 때 이 수를 같이 보여 준다.
 *
 * <p>프로세스 안의 수다. 서버가 하나라 그게 곧 전체이고, 늘어나면 Redis 로 옮겨야 한다.
 */
@Component
public class HistoryTraffic {
    private final AtomicInteger inFlight = new AtomicInteger();

    public void enter() {
        inFlight.incrementAndGet();
    }

    public void leave() {
        inFlight.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    public int current() {
        return inFlight.get();
    }
}
