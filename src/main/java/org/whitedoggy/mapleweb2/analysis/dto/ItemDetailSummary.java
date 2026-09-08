package org.whitedoggy.mapleweb2.analysis.dto;

import org.whitedoggy.mapleweb2.domain.item.data.ItemStatLine;

import java.util.List;

/** 게임 아이템 창 한 장. 교체를 펼쳐 이전·이후를 나란히 읽는다. */
public record ItemDetailSummary(
        String name,
        String icon,
        Integer starForce,
        Integer scrollUpgrade,
        Integer requiredLevel,
        String potentialGrade,
        String additionalPotentialGrade,
        String expired,
        List<ItemStatLine> stats,
        List<String> descriptionLines,
        List<String> potentialLines,
        List<String> additionalPotentialLines,
        List<String> exceptionalLines
) {
}
