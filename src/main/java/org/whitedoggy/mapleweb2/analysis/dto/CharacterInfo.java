package org.whitedoggy.mapleweb2.analysis.dto;

public record CharacterInfo(
        String name,
        String className,
        Integer level,
        String guild,
        String world,
        String image
) {
}
