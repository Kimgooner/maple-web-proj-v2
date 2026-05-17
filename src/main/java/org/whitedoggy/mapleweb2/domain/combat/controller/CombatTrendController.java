package org.whitedoggy.mapleweb2.domain.combat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.whitedoggy.mapleweb2.domain.combat.data.CombatTrendResponse;
import org.whitedoggy.mapleweb2.domain.combat.data.StatSheetComparisonResponse;
import org.whitedoggy.mapleweb2.domain.combat.service.CombatTrendService;
import org.whitedoggy.mapleweb2.domain.combat.service.StatSheetComparisonService;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class CombatTrendController {
    private final CombatTrendService combatTrendService;
    private final StatSheetComparisonService statSheetComparisonService;

    @GetMapping("/api/combat/trend")
    public Mono<CombatTrendResponse> getCombatTrend(@RequestParam String characterName) {
        return combatTrendService.getCombatTrend(characterName);
    }

    @GetMapping("/api/combat/stat-sheets")
    public Mono<StatSheetComparisonResponse> getStatSheets(
            @RequestParam String characterName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return statSheetComparisonService.getStatSheets(characterName, date);
    }
}
