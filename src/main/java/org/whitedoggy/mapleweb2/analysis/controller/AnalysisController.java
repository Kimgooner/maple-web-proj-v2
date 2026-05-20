package org.whitedoggy.mapleweb2.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.whitedoggy.mapleweb2.analysis.dto.AnalysisCombatPowerResponse;
import org.whitedoggy.mapleweb2.analysis.service.AnalysisService;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AnalysisController {
    private final AnalysisService analysisService;

    @GetMapping("/api/analysis/combat-power")
    public Mono<AnalysisCombatPowerResponse> getCombatPower(
            @RequestParam String characterName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return analysisService.getCombatPower(characterName, date);
    }

    @GetMapping("/api/analysis/combat-power/monthly")
    public Mono<List<AnalysisCombatPowerResponse>> getMonthlyCombatPowers(@RequestParam String characterName) {
        return analysisService.getMonthlyCombatPowers(characterName);
    }

    @GetMapping("/api/analysis/combat-power/yearly")
    public Mono<List<AnalysisCombatPowerResponse>> getYearlyCombatPowers(@RequestParam String characterName) {
        return analysisService.getYearlyCombatPowers(characterName);
    }
}
