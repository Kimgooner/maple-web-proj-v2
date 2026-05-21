package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.dto.AnalysisCombatPowerResponse;
import org.whitedoggy.mapleweb2.analysis.dto.ChangeSlotSummary;
import org.whitedoggy.mapleweb2.analysis.dto.ChangeSourceSummary;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerSummary;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerChangeSummary;
import org.whitedoggy.mapleweb2.analysis.dto.DataSheetResponse;
import org.whitedoggy.mapleweb2.analysis.dto.StatDeltaSummary;
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
    private final DataSheetDiffService dataSheetDiffService;

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
                        .sort(Comparator.comparing(response -> response.CurrentPresetDataSheet().getDate()))
                        .collectList()
                        .map(this::toHistoricalResponses)
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

        return toResponse(current, combat, null);
    }

    private List<AnalysisCombatPowerResponse> toHistoricalResponses(List<DataSheetResponse> responses) {
        List<PreparedResponse> preparedResponses = responses.stream()
                .map(this::prepareResponse)
                .sorted(Comparator.comparing(preparedResponse -> preparedResponse.current().getDate()))
                .toList();

        List<AnalysisCombatPowerResponse> result = new ArrayList<>();
        for (int index = 0; index < preparedResponses.size(); index++) {
            PreparedResponse current = preparedResponses.get(index);
            CombatPowerChangeSummary changeSummary = null;
            if (index > 0) {
                PreparedResponse previous = preparedResponses.get(index - 1);
                changeSummary = toChangeSummary(dataSheetDiffService.diff(previous.combat(), current.combat()));
            }

            result.add(toResponse(current.current(), current.combat(), changeSummary));
        }
        return result;
    }

    private PreparedResponse prepareResponse(DataSheetResponse response) {
        return new PreparedResponse(
                prepareDataSheet(response.CurrentPresetDataSheet()),
                prepareDataSheet(response.CombatPresetdataSheet())
        );
    }

    private AnalysisCombatPowerResponse toResponse(DataSheet current, DataSheet combat, CombatPowerChangeSummary changeSummary) {
        return new AnalysisCombatPowerResponse(
                current.getCharacterName(),
                current.getCharacterClass(),
                current.getDate(),
                buildCurrentSummary(current),
                buildCombatSummary(combat, changeSummary)
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
                dataSheet.isLucidTransformSuspected(),
                null
        );
    }

    private CombatPowerSummary buildCombatSummary(DataSheet dataSheet, CombatPowerChangeSummary changeSummary) {
        return new CombatPowerSummary(
                combatPowerCalculator.estimateCombatPower(dataSheet),
                null,
                null,
                null,
                dataSheet.isLucidTransformSuspected(),
                changeSummary
        );
    }

    private CombatPowerChangeSummary toChangeSummary(DataSheetDiffService.CombatPresetDiff diff) {
        return new CombatPowerChangeSummary(
                diff.coreChanges().stream().map(this::toSourceSummary).toList(),
                diff.petChanges().stream().map(this::toSlotSummary).toList(),
                diff.cashChanges().stream().map(this::toSlotSummary).toList(),
                diff.itemChanges().stream().map(this::toSlotSummary).toList()
        );
    }

    private ChangeSourceSummary toSourceSummary(DataSheetDiffService.ChangeSummary changeSummary) {
        return new ChangeSourceSummary(
                changeSummary.source(),
                changeSummary.deltas().stream().map(this::toStatDeltaSummary).toList()
        );
    }

    private ChangeSlotSummary toSlotSummary(DataSheetDiffService.SlotChangeSummary changeSummary) {
        return new ChangeSlotSummary(
                changeSummary.slot(),
                changeSummary.changeType(),
                changeSummary.previousItemName(),
                changeSummary.currentItemName(),
                changeSummary.deltas().stream().map(this::toStatDeltaSummary).toList()
        );
    }

    private StatDeltaSummary toStatDeltaSummary(DataSheetDiffService.StatDelta statDelta) {
        return new StatDeltaSummary(statDelta.statName(), statDelta.delta());
    }

    private record PreparedResponse(
            DataSheet current,
            DataSheet combat
    ) {
    }
}
