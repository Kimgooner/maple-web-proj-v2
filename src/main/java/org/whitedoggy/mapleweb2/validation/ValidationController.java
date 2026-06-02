package org.whitedoggy.mapleweb2.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.whitedoggy.mapleweb2.validation.dto.CombatPowerValidationResponse;
import org.whitedoggy.mapleweb2.validation.dto.RankingSampleResponse;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class ValidationController {
    private final RankingSampleService rankingSampleService;
    private final CombatPowerValidationService combatPowerValidationService;

    @GetMapping("/api/validation/ranking-samples")
    public Mono<RankingSampleResponse> getRankingSamples() {
        return rankingSampleService.getSamples();
    }

    @GetMapping("/api/validation/combat-power")
    public Mono<CombatPowerValidationResponse> validateCombatPower() {
        return combatPowerValidationService.validateCurrentCombatPower();
    }
}
