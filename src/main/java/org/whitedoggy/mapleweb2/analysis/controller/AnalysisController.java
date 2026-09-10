package org.whitedoggy.mapleweb2.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.whitedoggy.mapleweb2.analysis.dto.AnalysisResponse;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerDetailResponse;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentCombatPowerDebugResponse;
import org.whitedoggy.mapleweb2.analysis.history.CombatPowerHistoryResponse;
import org.whitedoggy.mapleweb2.analysis.history.CombatPowerHistoryService;
import org.whitedoggy.mapleweb2.analysis.history.HistoryRange;
import org.whitedoggy.mapleweb2.analysis.ranking.LevelBandStatsResponse;
import org.whitedoggy.mapleweb2.analysis.ranking.LevelBandStatsService;
import org.whitedoggy.mapleweb2.analysis.service.AnalysisService;
import org.whitedoggy.mapleweb2.validation.CurrentCombatPowerDebugService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AnalysisController {
    private final AnalysisService analysisService;
    private final CurrentCombatPowerDebugService currentCombatPowerDebugService;
    private final CombatPowerHistoryService combatPowerHistoryService;
    private final LevelBandStatsService levelBandStatsService;

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

    /**
     * 전투력 추이. {@code range=daily} 는 오늘 포함 30일, {@code range=monthly} 는
     * 이번 달 포함 12개월(각 달 1일)이다. 캐릭터 생성 이전 구간은 잘라내고
     * {@code truncated} 로 알린다.
     */
    @GetMapping("/api/analysis/combat-power/history")
    public Mono<CombatPowerHistoryResponse> getCombatPowerHistory(
            @RequestParam String characterName,
            @RequestParam(defaultValue = "daily") String range
    ) {
        return combatPowerHistoryService.getHistory(characterName, HistoryRange.from(range));
    }

    /**
     * 장비 프리셋이 갈린 날짜를 되돌려 다시 계산한 추이.
     *
     * <p>보스 프리셋을 고르는 점수가 같아 하루만 다른 번호가 뽑히면, 캐릭터는 아무것도 안
     * 했는데 그래프에 골짜기가 생긴다. 이 구간에서 제일 많이 쓴 번호를 정답으로 보고 그날만
     * 다시 계산한다. 화면의 "전투력이 이상해요!" 가 이 길로 온다.
     */
    @GetMapping("/api/analysis/combat-power/history/repair")
    public Mono<CombatPowerHistoryResponse> repairCombatPowerHistory(
            @RequestParam String characterName,
            @RequestParam(defaultValue = "daily") String range
    ) {
        return combatPowerHistoryService.repairHistory(characterName, HistoryRange.from(range));
    }

    /** 위와 같은 조회를 진행 상황과 함께 흘려보낸다. 이벤트: meta → point... → done (실패 시 error). */
    @GetMapping(value = "/api/analysis/combat-power/history/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamCombatPowerHistory(
            @RequestParam String characterName,
            @RequestParam(defaultValue = "daily") String range
    ) {
        return combatPowerHistoryService.streamHistory(characterName, HistoryRange.from(range));
    }

    @GetMapping("/api/analysis/combat-power/detail")
    public Mono<CombatPowerDetailResponse> getCombatPowerDetail(
            @RequestParam String ocid,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate previousDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentDate,
            /** 차트에서 프리셋을 되돌렸으면 그 번호. 변경 내역도 같은 번호로 맞춰 비교한다. */
            @RequestParam(required = false) Integer itemPreset
    ) {
        return analysisService.getCombatPowerDetail(ocid, previousDate, currentDate, itemPreset);
    }

    /**
     * 레벨 구간별 전투력 분포. 매주 월요일에 랭킹 표본으로 다시 재고, 가지고 있는 주를
     * 모두 준다 — 화면이 추이의 지점마다 그 시점의 주를 골라 기준선을 긋는다.
     */
    @GetMapping("/api/analysis/level-band-stats")
    public Mono<LevelBandStatsResponse> getLevelBandStats() {
        return levelBandStatsService.getStats();
    }

    @GetMapping("/api/analysis/combat-power/current-debug")
    public Mono<CurrentCombatPowerDebugResponse> getCurrentCombatPowerDebug(@RequestParam String characterName) {
        return currentCombatPowerDebugService.getCurrentDebug(characterName);
    }

    /**
     * 잘못된 쿼리 파라미터(예: range=weekly)나 없는 캐릭터는 400 이다.
     * 이 컨트롤러에만 건다 - 다른 컨트롤러의 동작은 그대로 둔다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", String.valueOf(error.getMessage())));
    }
}
