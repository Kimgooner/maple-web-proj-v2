package org.whitedoggy.mapleweb2.global.visit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * 캐릭터 조회 한 번을 센다. 컨트롤러를 건드리지 않으려고 필터로 둔다.
 *
 * <p>추이 조회만 센다 — 화면이 캐릭터 하나를 열 때 반드시 지나는 길이고, 상세·detail 은
 * 그 뒤에 여러 번 따라붙어 같이 세면 한 사람이 여러 번으로 잡힌다.
 */
@Component
@RequiredArgsConstructor
public class VisitCountingFilter implements WebFilter {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String COUNTED_PATH = "/api/analysis/combat-power/history";

    private final VisitCounter visitCounter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(COUNTED_PATH)) {
            return chain.filter(exchange);
        }
        String name = exchange.getRequest().getQueryParams().getFirst("characterName");
        return visitCounter.record(name, clientKey(exchange))
                .then(chain.filter(exchange));
    }

    /**
     * 방문자를 가르는 값. <b>IP 를 그대로 두지 않는다</b> — 날짜를 섞어 해시하고 앞 16자만 쓴다.
     *
     * <p>날짜를 섞으면 같은 사람이라도 날이 바뀌면 다른 값이 되어, 날짜를 가로질러 한 사람을
     * 따라갈 수 없다. 하루치 고유 방문자를 세는 데는 충분하고 그 이상은 못 한다.
     */
    private String clientKey(ServerWebExchange exchange) {
        // XFF 의 마지막 값을 쓴다. nginx 가 $proxy_add_x_forwarded_for 로 뒤에 덧붙이므로
        // 앞쪽은 클라이언트가 마음대로 적어 보낼 수 있고, 마지막 하나만 우리 프록시가 적은 것이다.
        // 지금은 집계만 왜곡되지만, 나중에 이 값으로 제한을 걸면 그대로 우회 통로가 된다.
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String ip = null;
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            ip = hops[hops.length - 1].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = remoteAddress(exchange);
        }
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((LocalDate.now(KST) + "|" + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return null;
        }
    }

    private String remoteAddress(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() == null
                ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }
}
