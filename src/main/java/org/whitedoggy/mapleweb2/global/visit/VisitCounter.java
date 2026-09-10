package org.whitedoggy.mapleweb2.global.visit;

import reactor.core.publisher.Mono;

/**
 * 하루치 조회를 센다.
 *
 * <p>브라우저로 나가는 것이 없다 — 스크립트도, 쿠키도, 외부 업체도 붙이지 않는다.
 * 서버가 요청을 받은 김에 Redis 카운터만 올린다. 그래서 동의 배너가 필요 없고,
 * 숫자는 서버 밖으로 나가지 않는다.
 */
public interface VisitCounter {
    /**
     * @param characterName 조회한 캐릭터
     * @param clientKey     방문자를 가르는 값(해시된 IP). 알 수 없으면 null
     */
    Mono<Void> record(String characterName, String clientKey);
}
