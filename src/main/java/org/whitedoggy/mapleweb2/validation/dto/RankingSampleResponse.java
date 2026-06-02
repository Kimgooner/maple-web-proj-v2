package org.whitedoggy.mapleweb2.validation.dto;

import java.time.LocalDate;
import java.util.List;

public record RankingSampleResponse(
        LocalDate date,
        List<RankingSampleGroup> groups
) {
}
