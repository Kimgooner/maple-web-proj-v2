package org.whitedoggy.mapleweb2.domain.union.artifact;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArtifactParser {
    public List<String> getArtifactEffects(JsonNode artifact) {
        List<String> effects = new ArrayList<>();
        for (JsonNode effect : artifact.path("union_artifact_effect")) {
            EffectTextSplitter.addSplit(effects, Jsons.text(effect, "name"));
        }
        return effects;
    }
}
