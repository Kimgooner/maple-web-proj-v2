package org.whitedoggy.mapleweb2.analysis.data;

import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * @param missingDocuments 끝내 받아 오지 못한 문서. {@code SnapshotService} 가 실패한 문서를
 *                         {@code NullNode} 로 채워 나머지 계산을 살리므로, 여기 담긴 만큼이
 *                         빠진 채로 계산된다. API 가 "그런 데이터는 없다"고 답한 문서는
 *                         (챌린저스 월드의 유니온 챔피언처럼) 다시 물어도 같으므로 담지 않는다.
 */
public record CharacterSnapshot(
        String ocid,
        LocalDate date,
        Map<NexonEndpoint, JsonNode> documents,
        Set<NexonEndpoint> missingDocuments
) {
    public CharacterSnapshot(String ocid, LocalDate date, Map<NexonEndpoint, JsonNode> documents) {
        this(ocid, date, documents, Set.of());
    }

    public JsonNode document(NexonEndpoint endpoint) {return documents.get(endpoint);}

    public boolean hasMissingDocuments() {
        return !missingDocuments.isEmpty();
    }
}
