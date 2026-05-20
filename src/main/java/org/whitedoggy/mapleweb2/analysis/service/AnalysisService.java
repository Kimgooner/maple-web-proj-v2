package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.dto.AnalysisCombatPowerResponse;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerSummary;
import org.whitedoggy.mapleweb2.analysis.dto.DataSheetResponse;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NexonApiClient nexonApiClient;
    private final DataSheetBuilder dataSheetBuilder;
    private final CombatPowerCalculator combatPowerCalculator;

    public Mono<AnalysisCombatPowerResponse> getCombatPower(String characterName, LocalDate date) {
        return dataSheetBuilder.getStatSheets(characterName, date)
                .map(this::toResponse);
    }

    public Mono<List<AnalysisCombatPowerResponse>> getMonthlyCombatPowers(String characterName) {
        LocalDate today = LocalDate.now(KST);
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            dates.add(today.minusDays(i * 2L));
        }
        return getCombatPowers(characterName, today, dates);
    }

    public Mono<List<AnalysisCombatPowerResponse>> getYearlyCombatPowers(String characterName) {
        LocalDate today = LocalDate.now(KST);
        List<LocalDate> dates = new ArrayList<>();
        for (int monthOffset = 1; monthOffset <= 11; monthOffset++) {
            dates.add(today.minusMonths(monthOffset).withDayOfMonth(15));
        }
        return getCombatPowers(characterName, today, dates);
    }

    private Mono<List<AnalysisCombatPowerResponse>> getCombatPowers(String characterName, LocalDate today, List<LocalDate> historicalDates) {
        return nexonApiClient.getOcid(characterName)
                .flatMap(ocidResponse -> Flux.concat(
                                nexonApiClient.fetchCurrentSnapshot(ocidResponse.ocid(), today),
                                Flux.fromIterable(historicalDates)
                                        .concatMap(date -> nexonApiClient.fetchSnapshot(ocidResponse.ocid(), date))
                        )
                        .map(dataSheetBuilder::buildFromSnapshot)
                        .filter(this::isSupported)
                        .map(this::toResponse)
                        .sort(Comparator.comparing(AnalysisCombatPowerResponse::date))
                        .collectList()
                );
    }

    private boolean isSupported(DataSheetResponse response) {
        DataSheet current = response.CurrentPresetDataSheet();
        return current != null
                && current.getCharacterLevel() != null
                && current.getCharacterLevel() >= 200
                && current.getCharacterName() != null
                && !current.getCharacterName().isBlank();
    }

    private AnalysisCombatPowerResponse toResponse(DataSheetResponse response) {
        DataSheet current = prepareDataSheet(response.CurrentPresetDataSheet());
        DataSheet combat = prepareDataSheet(response.CombatPresetdataSheet());

        return new AnalysisCombatPowerResponse(
                current.getCharacterName(),
                current.getCharacterClass(),
                current.getDate(),
                buildCurrentSummary(current),
                buildCombatSummary(combat)
        );
    }

    private DataSheet prepareDataSheet(DataSheet dataSheet) {
        dataSheet.setSumSheet(new StatSheet("총합"));
        dataSheet.buildSum();
        return dataSheet;
    }

    private CombatPowerSummary buildCurrentSummary(DataSheet dataSheet) {
        long estimatedCombatPower = combatPowerCalculator.estimateCombatPower(dataSheet);
        long currentCombatPower = dataSheet.getCurrentCombatPower();
        long difference = estimatedCombatPower - currentCombatPower;
        Double errorRatePercent = currentCombatPower == 0
                ? null
                : Math.abs(difference) * 100.0 / currentCombatPower;

        return new CombatPowerSummary(
                estimatedCombatPower,
                currentCombatPower,
                difference,
                errorRatePercent,
                dataSheet.isLucidTransformSuspected()
        );
    }

    private CombatPowerSummary buildCombatSummary(DataSheet dataSheet) {
        return new CombatPowerSummary(
                combatPowerCalculator.estimateCombatPower(dataSheet),
                null,
                null,
                null,
                dataSheet.isLucidTransformSuspected()
        );
    }
}
