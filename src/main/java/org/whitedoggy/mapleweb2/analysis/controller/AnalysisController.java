package org.whitedoggy.mapleweb2.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.whitedoggy.mapleweb2.analysis.dto.AnalysisResponse;
import org.whitedoggy.mapleweb2.analysis.service.AnalysisService;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class AnalysisController {
    private final AnalysisService analysisService;

    @GetMapping("/api/analysis/combat-power")
    public Mono<AnalysisResponse> getCombatPower(
            @RequestParam String characterName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return analysisService.getCombatPower(characterName, date);
    }

    @GetMapping("/api/analysis/combat-power/monthly")
    public Mono<AnalysisResponse> getMonthlyCombatPowers(@RequestParam String characterName) {
        return analysisService.getMonthlyCombatPowers(characterName);
    }

    @GetMapping("/api/analysis/combat-power/yearly")
    public Mono<AnalysisResponse> getYearlyCombatPowers(@RequestParam String characterName) {
        return analysisService.getYearlyCombatPowers(characterName);
    }
}
