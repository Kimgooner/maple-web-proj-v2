package org.whitedoggy.mapleweb2.domain.union.artifact;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.common.support.ExpiryDates;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 유니온 아티팩트 파서.
 *
 * <p>API의 {@code union_artifact_effect}는 완성된 효과 문자열을 주지만 <b>비어 있는 캐릭터가 있다</b>
 * (픽스처 105건 중 2건). 그래서 {@code union_artifact_crystal}에서 직접 계산한다.
 *
 * <p>효과 레벨은 <b>같은 옵션을 가진 크리스탈들의 레벨 합</b>이며 상한이 있다.
 * 크리스탈마다 따로 계산해 더하면 상한을 넘겨 과대계산되므로, 옵션별로 레벨을 먼저 합치고 자른다.
 *
 * <p>유효기간이 지난 크리스탈은 제외한다. {@code validity_flag}만으로는 부족하다 —
 * 캐릭터가 게임에 접속하지 않으면 만료됐는데도 flag가 0으로 남아 있는 경우가 있다.
 */
@Component
@RequiredArgsConstructor
public class ArtifactParser {

    private final ArtifactData artifactData;

    /** API가 완성해 준 효과 목록. 비어 있을 수 있다. */
    public List<String> getArtifactEffects(JsonNode artifact) {
        List<String> effects = new ArrayList<>();
        for (JsonNode effect : artifact.path("union_artifact_effect")) {
            EffectTextSplitter.addSplit(effects, Jsons.text(effect, "name"));
        }
        return effects;
    }

    /**
     * @param referenceDate 만료 판정 기준일. 스냅샷이 가리키는 시점이다.
     */
    public ArtifactParseResult parseCrystals(JsonNode crystal, LocalDate referenceDate) {
        Map<String, Integer> levelByOption = new LinkedHashMap<>();
        int expired = 0;
        for (JsonNode artifact : crystal.path("union_artifact_crystal")) {
            if ("1".equals(Jsons.text(artifact, "validity_flag"))) {
                expired++;
                continue;
            }
            if (ExpiryDates.isExpired(Jsons.text(artifact, "date_expire"), referenceDate)) {
                expired++;
                continue;
            }
            int level = Jsons.optionalInt(artifact, "level").orElse(0);
            for (int index = 1; index <= 3; index++) {
                String option = Jsons.text(artifact, "crystal_option_name_" + index);
                if (!option.isBlank()) {
                    levelByOption.merge(option, level, Integer::sum);
                }
            }
        }

        List<String> effects = new ArrayList<>();
        levelByOption.forEach((option, level) -> {
            String effect = toEffect(option, artifactData.capLevel(level));
            if (!effect.isBlank()) {
                EffectTextSplitter.addSplit(effects, effect);
            }
        });
        return new ArtifactParseResult(effects, expired);
    }

    /** 만료일이 기준일보다 앞서면 효과가 사라진 것으로 본다. 만료일이 없으면 영구다. */

    private String toEffect(String optionName, int level) {
        ArtifactData.Option option = artifactData.optionOf(optionName);
        if (option == null || level <= 0) {
            return "";
        }
        double amount = option.value() * level;
        String number = amount == Math.rint(amount)
                ? String.valueOf((long) amount)
                : String.valueOf(amount);
        return option.text() + " " + number + (option.percent() ? "%" : "") + " 증가";
    }
}
