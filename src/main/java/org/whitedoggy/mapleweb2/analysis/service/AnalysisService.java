package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.AnalysisDates;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.dto.AnalysisCombatPowerResponse;
import org.whitedoggy.mapleweb2.analysis.dto.AnalysisResponse;
import org.whitedoggy.mapleweb2.analysis.dto.ChangeSlotSummary;
import org.whitedoggy.mapleweb2.analysis.dto.ItemDetailSummary;
import org.whitedoggy.mapleweb2.analysis.dto.ChangeSourceSummary;
import org.whitedoggy.mapleweb2.analysis.dto.EntryChangeSummary;
import org.whitedoggy.mapleweb2.analysis.dto.CharacterInfo;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerChangeSummary;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerDetailResponse;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerSummary;
import org.whitedoggy.mapleweb2.analysis.dto.DataSheetByDate;
import org.whitedoggy.mapleweb2.analysis.dto.StatDeltaGroupSummary;
import org.whitedoggy.mapleweb2.analysis.dto.StatDeltaSummary;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
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

    private final DataSheetService dataSheetService;
    private final CombatCalculationService combatCalculationService;
    private final DataSheetCompareService dataSheetCompareService;
    private final OcidService ocidService;
    private final DateService dateService;
    private final SnapshotService snapshotService;
    private final BasicParser basicParser;
    private final StatParser statParser;
    private final GameData gameData;

    public Mono<AnalysisResponse> getCombatPower(String characterName, LocalDate date) {
        return ocidService.getOcid(characterName)
                .flatMap(ocid -> snapshotService.getSnapshotByOcid(ocid, date))
                .map(snapshot -> buildAnalysisResponse(snapshot, List.of(buildDataSheetByDate(snapshot))));
    }

    public Mono<AnalysisResponse> getMonthlyCombatPowers(String characterName) {
        return getAnalysis(characterName, "monthly");
    }

    public Mono<AnalysisResponse> getYearlyCombatPowers(String characterName) {
        return getAnalysis(characterName, "yearly");
    }

    public Mono<CombatPowerDetailResponse> getCombatPowerDetail(
            String ocid,
            LocalDate previousDate,
            LocalDate currentDate
    ) {
        return Mono.zip(
                        getPreparedCombatDataSheet(ocid, previousDate),
                        getPreparedCombatDataSheet(ocid, currentDate)
                )
                .map(tuple -> buildCombatPowerDetailResponse(
                        ocid,
                        previousDate,
                        currentDate,
                        tuple.getT1(),
                        tuple.getT2()
                ));
    }

    public Mono<AnalysisResponse> getAnalysis(String characterName, String dateType) {
        AnalysisDates dates = dateService.getDates(dateType);

        return ocidService.getOcid(characterName)
                .flatMap(ocid -> snapshotService.getCurrentSnapshotByOcid(ocid, dates.today())
                        .flatMap(todaySnapshot -> Flux.concat(
                                        Mono.just(buildPreparedEntry(todaySnapshot)),
                                        Flux.fromIterable(dates.historicalDates())
                                                .concatMap(date -> dataSheetService.getOrLoadDataSheet(
                                                                ocid,
                                                                date,
                                                                () -> snapshotService.getSnapshotByOcid(ocid, date)
                                                        )
                                                        .map(dataSheet -> new PreparedEntry(date, null, null, prepareDataSheet(dataSheet), null))
                                                )
                                )
                                .collectList()
                                .map(entries -> buildAnalysisResponse(todaySnapshot, toDataSheetEntries(entries)))
                        ));
    }

    private DataSheetByDate buildDataSheetByDate(CharacterSnapshot snapshot) {
        PreparedEntry entry = buildPreparedEntry(snapshot);
        return new DataSheetByDate(entry.date(), entry.combatDataSheet());
    }

    private PreparedEntry buildPreparedEntry(CharacterSnapshot snapshot) {
        DataSheet currentDataSheet = prepareDataSheet(dataSheetService.getCurrentDataSheet(snapshot));
        DataSheet combatDataSheet = prepareDataSheet(dataSheetService.getCombatDataSheet(snapshot));
        Long apiCombatPower = currentCombatPower(snapshot);
        return new PreparedEntry(snapshot.date(), currentDataSheet, apiCombatPower, combatDataSheet, null);
    }

    private AnalysisResponse buildAnalysisResponse(CharacterSnapshot todaySnapshot, List<DataSheetByDate> entries) {
        return new AnalysisResponse(
                todaySnapshot.ocid(),
                buildCharacterInfo(todaySnapshot),
                entries
        );
    }

    private CharacterInfo buildCharacterInfo(CharacterSnapshot snapshot) {
        String characterClass = basicParser.characterClass(snapshot.document(NexonEndpoint.BASIC));
        return CharacterInfo.of(
                basicParser.characterName(snapshot.document(NexonEndpoint.BASIC)),
                characterClass,
                basicParser.characterLevel(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterGuild(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterWorld(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterImage(snapshot.document(NexonEndpoint.BASIC)),
                gameData
        );
    }

    private List<DataSheetByDate> toDataSheetEntries(List<PreparedEntry> entries) {
        List<PreparedEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(PreparedEntry::date))
                .toList();

        List<DataSheetByDate> entriesByDate = new ArrayList<>();
        for (PreparedEntry entry : sortedEntries) {
            entriesByDate.add(new DataSheetByDate(entry.date(), entry.combatDataSheet()));
        }
        return entriesByDate;
    }

    private Mono<DataSheet> getPreparedCombatDataSheet(String ocid, LocalDate date) {
        return dataSheetService.getOrLoadDataSheet(
                        ocid,
                        date,
                        () -> loadSnapshotByDate(ocid, date)
                )
                .map(this::prepareDataSheet);
    }

    private Mono<CharacterSnapshot> loadSnapshotByDate(String ocid, LocalDate date) {
        if (LocalDate.now(KST).equals(date)) {
            return snapshotService.getCurrentSnapshotByOcid(ocid, date);
        }
        return snapshotService.getSnapshotByOcid(ocid, date);
    }

    private CombatPowerDetailResponse buildCombatPowerDetailResponse(
            String ocid,
            LocalDate previousDate,
            LocalDate currentDate,
            DataSheet previousDataSheet,
            DataSheet currentDataSheet
    ) {
        DataSheetCompareService.CombatPresetDiff diff = dataSheetCompareService.diff(previousDataSheet, currentDataSheet);
        return new CombatPowerDetailResponse(
                ocid,
                previousDate,
                currentDate,
                toChangeSummary(diff)
        );
    }

    private DataSheet prepareDataSheet(DataSheet dataSheet) {
        dataSheet.setSumSheet(new StatSheet("sum"));
        dataSheet.buildSum();
        return dataSheet;
    }

    private Long currentCombatPower(CharacterSnapshot snapshot) {
        String currentCombatPower = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
        if (currentCombatPower == null || currentCombatPower.isBlank()) {
            return null;
        }
        return (long) Math.floor(Jsons.parseDouble(currentCombatPower));
    }

    private CombatPowerSummary buildCurrentSummary(DataSheet dataSheet, Long apiCombatPower) {
        long estimatedCombatPower = estimatedCombatPower(dataSheet);
        Long difference = apiCombatPower == null ? null : estimatedCombatPower - apiCombatPower;
        Double errorRatePercent = apiCombatPower == null || apiCombatPower == 0
                ? null
                : Math.abs(difference) * 100.0 / apiCombatPower;

        return new CombatPowerSummary(
                estimatedCombatPower,
                apiCombatPower,
                difference,
                errorRatePercent,
                dataSheet.isLucidTransformSuspected(),
                null
        );
    }

    private CombatPowerSummary buildCombatSummary(DataSheet dataSheet, CombatPowerChangeSummary changeSummary) {
        return new CombatPowerSummary(
                estimatedCombatPower(dataSheet),
                null,
                null,
                null,
                dataSheet.isLucidTransformSuspected(),
                changeSummary
        );
    }

    private long estimatedCombatPower(DataSheet dataSheet) {
        return dataSheet.getCombatPower() == null
                ? combatCalculationService.estimateCombatPower(dataSheet, "", 0)
                : dataSheet.getCombatPower();
    }

    private CombatPowerChangeSummary toChangeSummary(DataSheetCompareService.CombatPresetDiff diff) {
        return new CombatPowerChangeSummary(
                diff.coreChanges().stream().map(this::toSourceSummary).toList(),
                diff.petChanges().stream().map(this::toSlotSummary).toList(),
                diff.cashChanges().stream().map(this::toSlotSummary).toList(),
                diff.itemChanges().stream().map(this::toSlotSummary).toList()
        );
    }

    private ChangeSourceSummary toSourceSummary(DataSheetCompareService.ChangeSummary changeSummary) {
        return new ChangeSourceSummary(
                changeSummary.source(),
                changeSummary.deltas().stream().map(this::toStatDeltaSummary).toList(),
                changeSummary.entries().stream()
                        .map(e -> new EntryChangeSummary(e.name(), e.previous(), e.current(), e.icon(), e.detail()))
                        .toList()
        );
    }

    private ChangeSlotSummary toSlotSummary(DataSheetCompareService.SlotChangeSummary changeSummary) {
        return new ChangeSlotSummary(
                changeSummary.slot(),
                changeSummary.previousSlot(),
                changeSummary.currentSlot(),
                changeSummary.changeType(),
                changeSummary.previousItemName(),
                changeSummary.currentItemName(),
                changeSummary.previousItemIcon(),
                changeSummary.currentItemIcon(),
                changeSummary.deltas().stream().map(this::toStatDeltaSummary).toList(),
                changeSummary.deltaGroups().stream().map(this::toStatDeltaGroupSummary).toList(),
                toItemDetailSummary(changeSummary.previousItem()),
                toItemDetailSummary(changeSummary.currentItem())
        );
    }

    private ItemDetailSummary toItemDetailSummary(DataSheetCompareService.ItemDetail item) {
        if (item == null) {
            return null;
        }
        return new ItemDetailSummary(
                item.name(), item.icon(), item.starForce(), item.scrollUpgrade(), item.requiredLevel(),
                item.potentialGrade(), item.additionalPotentialGrade(), item.expired(),
                item.stats(), item.descriptionLines(),
                item.potentialLines(), item.additionalPotentialLines(), item.exceptionalLines());
    }

    private StatDeltaGroupSummary toStatDeltaGroupSummary(DataSheetCompareService.StatDeltaGroup group) {
        return new StatDeltaGroupSummary(
                group.category(),
                group.deltas().stream().map(this::toStatDeltaSummary).toList());
    }

    private StatDeltaSummary toStatDeltaSummary(DataSheetCompareService.StatDelta statDelta) {
        return new StatDeltaSummary(statDelta.statName(), statDelta.delta());
    }

    private record PreparedEntry(
            LocalDate date,
            DataSheet currentDataSheet,
            Long apiCombatPower,
            DataSheet combatDataSheet,
            CombatPowerChangeSummary changeSummary
    ) {
    }
}
