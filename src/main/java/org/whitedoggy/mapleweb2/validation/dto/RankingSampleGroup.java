package org.whitedoggy.mapleweb2.validation.dto;

import java.util.List;

public record RankingSampleGroup(
        String groupName,
        int requestedCount,
        List<RankingCharacterSample> characters
) {
}
