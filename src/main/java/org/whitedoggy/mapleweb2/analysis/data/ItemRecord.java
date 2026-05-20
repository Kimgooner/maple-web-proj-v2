package org.whitedoggy.mapleweb2.analysis.data;

import java.util.List;

public record ItemRecord(
        String slot,
        String name,
        List<String> effects
) {
}
