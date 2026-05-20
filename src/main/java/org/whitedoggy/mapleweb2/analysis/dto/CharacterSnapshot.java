package org.whitedoggy.mapleweb2.analysis.dto;

import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

public record CharacterSnapshot(
        String ocid,
        LocalDate date,
        Map<NexonEndpoint, JsonNode> documents
) {
    public JsonNode document(NexonEndpoint endpoint) {return documents.get(endpoint);}
}
