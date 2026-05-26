package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SnapshotService {
    private final NexonApiClient nexonApiClient;

    public Mono<CharacterSnapshot> getSnapshotByOcid(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, true);
    }

    public Mono<CharacterSnapshot> getCurrentSnapshotByOcid(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, false);
    }

    private Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date, boolean includeDateParam) {
        return Mono.zip(
                        fetchBasic(ocid, date, includeDateParam),
                        fetchStat(ocid, date, includeDateParam),
                        fetchItemEquipment(ocid, date, includeDateParam),
                        fetchCashItemEquipment(ocid, date, includeDateParam),
                        fetchSetEffect(ocid, date, includeDateParam),
                        fetchSymbolEquipment(ocid, date, includeDateParam),
                        fetchPetEquipment(ocid, date, includeDateParam)
                )
                .zipWith(Mono.zip(
                        fetchHyperStat(ocid, date, includeDateParam),
                        fetchAbility(ocid, date, includeDateParam),
                        fetchSkill0(ocid, date, includeDateParam),
                        fetchHexaMatrixStat(ocid, date, includeDateParam),
                        fetchUnionRaider(ocid, date, includeDateParam),
                        fetchUnionChampion(ocid, date, includeDateParam),
                        fetchArtifact(ocid, date, includeDateParam)
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

    private Mono<JsonNode> fetchBasic(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.BASIC, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchStat(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.STAT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchItemEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.ITEM_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchCashItemEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.CASH_ITEM_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchSetEffect(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.SET_EFFECT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchSymbolEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.SYMBOL_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchPetEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.PET_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchHyperStat(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.HYPER_STAT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchAbility(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.ABILITY, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchSkill0(String ocid, LocalDate date, boolean includeDateParam) {
        return nexonApiClient.getSkill0(ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorReturn(nullNode());
    }

    private Mono<JsonNode> fetchHexaMatrixStat(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.HEXA_MATRIX_STAT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchUnionRaider(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.UNION_RAIDER, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchUnionChampion(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.UNION_CHAMPION, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchArtifact(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.UNION_ARTIFACT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchEndpoint(NexonEndpoint endpoint, String ocid, LocalDate date, boolean includeDateParam) {
        return nexonApiClient.get(endpoint, ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorReturn(nullNode());
    }

    private Retry retrySpec() {
        return Retry.backoff(2, Duration.ofMillis(300))
                .filter(this::isRetryable);
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientRequestException) {
            return true;
        }
        if (throwable instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError()
                    || responseException.getStatusCode().value() == 429;
        }
        return false;
    }

    private JsonNode nullNode() {
        return tools.jackson.databind.node.NullNode.getInstance();
    }

}
