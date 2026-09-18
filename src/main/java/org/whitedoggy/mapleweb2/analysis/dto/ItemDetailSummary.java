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
        List<String> exceptionalLines,
        /** 무기 소울. 이름과 그것이 주는 줄(옵션·상시 공격력). 없으면 null·빈 목록 */
        String soulName,
        List<String> soulLines,
        /** 무기 소울 잠재(2026-09-17). 무기가 아니면 비어 있고 등급은 null */
        String soulPotentialGrade,
        List<String> soulPotentialLines
) {
}
