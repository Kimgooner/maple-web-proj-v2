package org.whitedoggy.mapleweb2.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.analysis.service.OcidService;
import org.whitedoggy.mapleweb2.analysis.service.SnapshotService;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import org.whitedoggy.mapleweb2.validation.dto.CombatPowerValidationGroup;
import org.whitedoggy.mapleweb2.validation.dto.CombatPowerValidationResponse;
import org.whitedoggy.mapleweb2.validation.dto.CombatPowerValidationResult;
import org.whitedoggy.mapleweb2.validation.dto.RankingCharacterSample;
import org.whitedoggy.mapleweb2.validation.dto.RankingSampleGroup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CombatPowerValidationService {
    private final RankingSampleService rankingSampleService;
    private final OcidService ocidService;
    private final SnapshotService snapshotService;
    private final DataSheetService dataSheetService;
    private final StatParser statParser;

    public Mono<CombatPowerValidationResponse> validateCurrentCombatPower() {
        return rankingSampleService.getSamples()
                .flatMap(samples -> Flux.fromIterable(samples.groups())
                        .concatMap(group -> validateGroup(samples.date(), group))
                        .collectList()
                        .map(groups -> new CombatPowerValidationResponse(samples.date(), groups)));
    }

    private Mono<CombatPowerValidationGroup> validateGroup(LocalDate date, RankingSampleGroup group) {
        return Flux.fromIterable(group.characters())
                .concatMap(character -> validateCharacter(date, character)
                        .onErrorResume(error -> Mono.just(errorResult(character, error))))
                .collectList()
                .map(results -> new CombatPowerValidationGroup(group.groupName(), results));
    }

    private Mono<CombatPowerValidationResult> validateCharacter(LocalDate date, RankingCharacterSample character) {
        return ocidService.getOcid(character.characterName())
                .flatMap(ocid -> snapshotService.getCurrentSnapshotByOcid(ocid, date))
                .map(snapshot -> toResult(character, snapshot));
    }

    private CombatPowerValidationResult toResult(RankingCharacterSample character, CharacterSnapshot snapshot) {
        DataSheet currentDataSheet = prepareDataSheet(dataSheetService.getCurrentDataSheet(snapshot));
        Long estimatedCombatPower = currentDataSheet.getCombatPower();
        Long currentCombatPower = currentCombatPower(snapshot);
        Long difference = currentCombatPower == null || estimatedCombatPower == null
                ? null
                : estimatedCombatPower - currentCombatPower;
        Double errorRatePercent = currentCombatPower == null || currentCombatPower == 0 || difference == null
                ? null
                : Math.abs(difference) * 100.0 / currentCombatPower;

        return new CombatPowerValidationResult(
                character.characterName(),
                character.worldName(),
                character.className(),
                character.characterLevel(),
                estimatedCombatPower,
                currentCombatPower,
                difference,
                errorRatePercent,
                currentDataSheet.isLucidTransformSuspected(),
                null
        );
    }

    private DataSheet prepareDataSheet(DataSheet dataSheet) {
        dataSheet.setSumSheet(new StatSheet("sum"));
        dataSheet.buildSum();
        return dataSheet;
    }

    private Long currentCombatPower(CharacterSnapshot snapshot) {
        String value = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
        if (value == null || value.isBlank()) {
            return null;
        }
        return (long) Math.floor(Jsons.parseDouble(value));
    }

    private CombatPowerValidationResult errorResult(RankingCharacterSample character, Throwable error) {
        return new CombatPowerValidationResult(
                character.characterName(),
                character.worldName(),
                character.className(),
                character.characterLevel(),
                null,
                null,
                null,
                null,
                false,
                error.getMessage()
        );
    }
}
