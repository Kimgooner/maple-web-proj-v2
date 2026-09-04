package org.whitedoggy.mapleweb2.global.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class FrontendRouterTest {

    private final WebTestClient client = WebTestClient
            .bindToRouterFunction(new FrontendRouter().frontendEntryRoute())
            .build();

    @Test
    void appRootRedirectsToIndex() {
        client.get().uri("/app").exchange()
                .expectStatus().isTemporaryRedirect()
                .expectHeader().location(FrontendRouter.ENTRY);
        client.get().uri("/app/").exchange()
                .expectStatus().isTemporaryRedirect()
                .expectHeader().location(FrontendRouter.ENTRY);
    }

    @Test
    void otherPathsAreNotHandled() {
        client.get().uri("/app/assets/x.js").exchange()
                .expectStatus().isNotFound();
        client.get().uri("/api/analysis/combat-power").exchange()
                .expectStatus().isNotFound();
    }
}
