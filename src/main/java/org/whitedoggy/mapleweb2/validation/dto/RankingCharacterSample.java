package org.whitedoggy.mapleweb2.validation.dto;

public record RankingCharacterSample(
        String characterName,
        String worldName,
        String className,
        String subClassName,
        Integer characterLevel,
        Integer ranking
) {
}
