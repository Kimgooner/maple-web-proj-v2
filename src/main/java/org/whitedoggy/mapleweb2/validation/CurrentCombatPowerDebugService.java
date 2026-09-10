package org.whitedoggy.mapleweb2.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.dto.CharacterInfo;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentCombatPowerDebugResponse;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentPresetInfo;
import org.whitedoggy.mapleweb2.analysis.dto.StatSheetSummary;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.analysis.service.OcidService;
import org.whitedoggy.mapleweb2.analysis.service.SnapshotService;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CurrentCombatPowerDebugService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OcidService ocidService;
    private final GameData gameData;
    private final SnapshotService snapshotService;
    private final DataSheetService dataSheetService;
    private final BasicParser basicParser;
    private final StatParser statParser;

    public Mono<CurrentCombatPowerDebugResponse> getCurrentDebug(String characterName) {
        LocalDate today = LocalDate.now(KST);
        return ocidService.getOcid(characterName)
                .flatMap(ocid -> snapshotService.getCurrentSnapshotByOcid(ocid, today))
                .map(this::toResponse);
    }

    private CurrentCombatPowerDebugResponse toResponse(CharacterSnapshot snapshot) {
        PresetSelection currentPreset = dataSheetService.getCurrentPresetSelection(snapshot);
        DataSheet currentDataSheet = prepareDataSheet(dataSheetService.getCurrentDataSheet(snapshot));
        Long estimatedCombatPower = currentDataSheet.getCombatPower();
        Long currentCombatPower = currentCombatPower(snapshot);
        Long difference = currentCombatPower == null || estimatedCombatPower == null
                ? null
                : estimatedCombatPower - currentCombatPower;
        Double errorRatePercent = currentCombatPower == null || currentCombatPower == 0 || difference == null
                ? null
                : Math.abs(difference) * 100.0 / currentCombatPower;

        return new CurrentCombatPowerDebugResponse(
                snapshot.ocid(),
                snapshot.date(),
                characterInfo(snapshot),
                new CurrentPresetInfo(
                        currentPreset.itemPreset(),
                        currentPreset.abilityPreset(),
                        currentPreset.hyperStatPreset(),
                        currentPreset.unionRaiderPreset()
                ),
                estimatedCombatPower,
                currentCombatPower,
                difference,
                errorRatePercent,
                currentDataSheet.isLucidTransformSuspected(),
                new CurrentCombatPowerDebugResponse.DataQuality(
                        currentDataSheet.isInactiveCharacter(),
                        currentDataSheet.isIncompleteSnapshot(),
                        currentDataSheet.isWeaponMissing(),
                        currentDataSheet.isUnknownWeapon(),
                        currentDataSheet.isWeaponNormalizationFailed(),
                        currentDataSheet.isUnionRaiderDataMissing(),
                        currentDataSheet.getExpiredArtifactCrystals(),
                        currentDataSheet.getExpiredCashItems(),
                        currentDataSheet.getExpiredPetEquipments(),
                        currentDataSheet.isExpiredTitleOption()
                ),
                statSheets(currentDataSheet)
        );
    }

    private DataSheet prepareDataSheet(DataSheet dataSheet) {
        dataSheet.setSumSheet(new StatSheet("sum"));
        dataSheet.buildSum();
        return dataSheet;
    }

    private CharacterInfo characterInfo(CharacterSnapshot snapshot) {
        return CharacterInfo.of(
                basicParser.characterName(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterClass(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterLevel(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterGuild(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterWorld(snapshot.document(NexonEndpoint.BASIC)),
                basicParser.characterImage(snapshot.document(NexonEndpoint.BASIC)),
                gameData
        );
    }

    private Long currentCombatPower(CharacterSnapshot snapshot) {
        String value = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
        if (value == null || value.isBlank()) {
            return null;
        }
        return (long) Math.floor(Jsons.parseDouble(value));
    }

    private Map<String, StatSheetSummary> statSheets(DataSheet dataSheet) {
        Map<String, StatSheetSummary> sheets = new LinkedHashMap<>();
        put(sheets, "abilityPoint", dataSheet.getAbilityPoint());
        put(sheets, "symbol", dataSheet.getSymbol());
        put(sheets, "skill", dataSheet.getSkill());
        put(sheets, "hexaStat", dataSheet.getHexaStat());
        put(sheets, "ability", dataSheet.getAbility());
        put(sheets, "hyperStat", dataSheet.getHyperStat());
        put(sheets, "setEffect", dataSheet.getSetEffect());
        put(sheets, "unionArtifact", dataSheet.getUnionArtifact());
        put(sheets, "unionChampion", dataSheet.getUnionChampion());
        put(sheets, "unionOccupied", dataSheet.getUnionOccupied());
        put(sheets, "unionRaider", dataSheet.getUnionRaider());
        putItemSheets(sheets, dataSheet.getPetEquip());
        putItemSheets(sheets, dataSheet.getCashEquip());
        putItemSheets(sheets, dataSheet.getItemEquip());
        return sheets;
    }

    private void put(Map<String, StatSheetSummary> sheets, String name, StatSheet sheet) {
        if (sheet != null) {
            sheets.put(name, statSummary(sheet));
        }
    }

    private void putItemSheets(Map<String, StatSheetSummary> sheets, Map<String, ItemSnapShot> items) {
        if (items == null) {
            return;
        }
        items.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sheets.put(entry.getKey(), statSummary(entry.getValue().getStatSheet())));
    }

    private StatSheetSummary statSummary(StatSheet sheet) {
        return new StatSheetSummary(
                sheet.getSTR(),
                sheet.getDEX(),
                sheet.getINT(),
                sheet.getLUK(),
                sheet.getHP(),
                sheet.getALL_STAT(),
                sheet.getSTR_PER_LEVEL9(),
                sheet.getDEX_PER_LEVEL9(),
                sheet.getINT_PER_LEVEL9(),
                sheet.getLUK_PER_LEVEL9(),
                sheet.getSTR_NO_PERCENT(),
                sheet.getDEX_NO_PERCENT(),
                sheet.getINT_NO_PERCENT(),
                sheet.getLUK_NO_PERCENT(),
                sheet.getHP_NO_PERCENT(),
                sheet.getALL_STAT_NO_PERCENT(),
                sheet.getATTACK_POWER(),
                sheet.getMAGIC_POWER(),
                sheet.getSTR_PERCENT(),
                sheet.getDEX_PERCENT(),
                sheet.getINT_PERCENT(),
                sheet.getLUK_PERCENT(),
                sheet.getHP_PERCENT(),
                sheet.getALL_STAT_PERCENT(),
                sheet.getATTACK_POWER_PERCENT(),
                sheet.getMAGIC_POWER_PERCENT(),
                sheet.getDAMAGE(),
                sheet.getBOSS_DAMAGE(),
                sheet.getCRITICAL_DAMAGE(),
                sheet.getFINAL_DAMAGE(),
                sheet.getCOOLDOWN_SECOND(),
                sheet.getCOOLDOWN_SKIP_PERCENT()
        );
    }
}
