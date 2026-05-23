package org.whitedoggy.mapleweb2.external.nexon.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.external.nexon.dto.OcidResponse;
import org.whitedoggy.mapleweb2.external.nexon.exception.NexonApiException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NexonApiClient {
    private final WebClient nexonWebClient;
    private final NexonRateLimiter nexonRateLimiter;

    public Mono<OcidResponse> getOcid(String characterName) {
        return nexonRateLimiter.acquire()
                .then(nexonWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/maplestory/v1/id")
                                .queryParam("character_name", characterName)
                                .build())
                        .retrieve()
                        .bodyToMono(OcidResponse.class));
    }

    public Mono<JsonNode> get(NexonEndpoint endpoint, String ocid, LocalDate date, boolean includeDateParam) {
        return nexonRateLimiter.acquire()
                .then(nexonWebClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(endpoint.path())
                                    .queryParam("ocid", ocid);
                            if (includeDateParam) {
                                uriBuilder.queryParam("date", date);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .bodyToMono(JsonNode.class));
    }

    public Mono<JsonNode> getSkill0(String ocid, LocalDate date, boolean includeDateParam) {
        return nexonRateLimiter.acquire()
                .then(nexonWebClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(NexonEndpoint.SKILL_0.path())
                                    .queryParam("ocid", ocid)
                                    .queryParam("character_skill_grade", "0");
                            if (includeDateParam) {
                                uriBuilder.queryParam("date", date);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .bodyToMono(JsonNode.class));
    }

    private static String requiredText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new NexonApiException("Nexon API response did not include required field: " + field);
        }
        return node.get(field).asText();
    }

    private static JsonNode nullNode() {
        return tools.jackson.databind.node.NullNode.getInstance();
    }

    public Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, true);
    }

    public Mono<CharacterSnapshot> fetchCurrentSnapshot(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, false);
    }

    private Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date, boolean includeDateParam) {
        return Mono.zip(
                        get(NexonEndpoint.BASIC, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.STAT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.ITEM_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.CASH_ITEM_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.SET_EFFECT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.SYMBOL_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.PET_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode())
                )
                .zipWith(Mono.zip(
                        get(NexonEndpoint.HYPER_STAT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.ABILITY, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        getSkill0(ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.HEXA_MATRIX_STAT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.UNION_RAIDER, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.UNION_CHAMPION, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.UNION_ARTIFACT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode())
                ))
                .map(tuple -> {
                    Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(NexonEndpoint.class);
                    documents.put(NexonEndpoint.BASIC, tuple.getT1().getT1());
                    documents.put(NexonEndpoint.STAT, tuple.getT1().getT2());
                    documents.put(NexonEndpoint.ITEM_EQUIPMENT, tuple.getT1().getT3());
                    documents.put(NexonEndpoint.CASH_ITEM_EQUIPMENT, tuple.getT1().getT4());
                    documents.put(NexonEndpoint.SET_EFFECT, tuple.getT1().getT5());
                    documents.put(NexonEndpoint.SYMBOL_EQUIPMENT, tuple.getT1().getT6());
                    documents.put(NexonEndpoint.PET_EQUIPMENT, tuple.getT1().getT7());
                    documents.put(NexonEndpoint.HYPER_STAT, tuple.getT2().getT1());
                    documents.put(NexonEndpoint.ABILITY, tuple.getT2().getT2());
                    documents.put(NexonEndpoint.SKILL_0, tuple.getT2().getT3());
                    documents.put(NexonEndpoint.HEXA_MATRIX_STAT, tuple.getT2().getT4());
                    documents.put(NexonEndpoint.UNION_RAIDER, tuple.getT2().getT5());
                    documents.put(NexonEndpoint.UNION_CHAMPION, tuple.getT2().getT6());
                    documents.put(NexonEndpoint.UNION_ARTIFACT, tuple.getT2().getT7());
                    return new CharacterSnapshot(ocid, date, documents);
                });
    }
}
