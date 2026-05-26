package org.whitedoggy.mapleweb2.analysis.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

public record StatContribution (
    StatSource source,
    String slot,
    String name,
    String icon,
    StatSheet sheet
) {}
