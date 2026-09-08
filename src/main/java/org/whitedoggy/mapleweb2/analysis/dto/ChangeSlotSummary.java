package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record ChangeSlotSummary(
        String slot,
        String previousSlot,
        String currentSlot,
        String changeType,
        String previousItemName,
        String currentItemName,
        String previousItemIcon,
        String currentItemIcon,
        List<StatDeltaSummary> deltas,
        List<StatDeltaGroupSummary> deltaGroups,
        /** 게임 아이템 창에 나오는 것들. 교체를 펼쳐 나란히 읽는다. 빈 자리면 null. */
        ItemDetailSummary previousItem,
        ItemDetailSummary currentItem
) {
}
