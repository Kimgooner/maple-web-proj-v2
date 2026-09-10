package org.whitedoggy.mapleweb2.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentCombatPowerDebugResponse;
import org.whitedoggy.mapleweb2.validation.dto.CombatPowerValidationResponse;
import org.whitedoggy.mapleweb2.validation.dto.RankingSampleResponse;
import reactor.core.publisher.Mono;

/**
 * 계산 정확도를 사람이 눈으로 보려고 만든 진단 도구.
 *
 * <p><b>프로덕션에서는 꺼 둔다.</b> nginx 가 {@code /api/} 를 통째로 넘기므로 이 길이 열려
 * 있으면 누구나 부를 수 있는데, {@code /api/validation/combat-power} 한 번이 넥슨 API 를
 * 약 900회 쓴다(랭킹 92 + 표본 45명 × 18). 반복해서 부르면 우리 서버가 넥슨 초당 한도를
 * 스스로 다 먹어, 정작 이용자의 캐릭터 조회가 줄을 서게 된다.
 *
 * <p>{@code /current-debug} 도 여기에 둔다. 화면이 부르지 않는 진단용인데 컨트롤러만
 * 달랐던 탓에 혼자 열려 있었다 - 한 번에 스냅샷 17회를 쓰고, 응답에 ocid 와 우리가 매긴
 * 데이터 품질 플래그가 그대로 실린다.
 *
 * <p>필요할 때는 로컬에서 {@code maple.validation.enabled=true} 로 켠다.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "maple.validation.enabled", havingValue = "true")
public class ValidationController {
    private final RankingSampleService rankingSampleService;
    private final CombatPowerValidationService combatPowerValidationService;
    private final CurrentCombatPowerDebugService currentCombatPowerDebugService;

    @GetMapping("/api/validation/ranking-samples")
    public Mono<RankingSampleResponse> getRankingSamples() {
        return rankingSampleService.getSamples();
    }

    @GetMapping("/api/validation/combat-power")
    public Mono<CombatPowerValidationResponse> validateCombatPower() {
        return combatPowerValidationService.validateCurrentCombatPower();
    }

    @GetMapping("/api/validation/current-debug")
    public Mono<CurrentCombatPowerDebugResponse> getCurrentDebug(@RequestParam String characterName) {
        return currentCombatPowerDebugService.getCurrentDebug(characterName);
    }
}
