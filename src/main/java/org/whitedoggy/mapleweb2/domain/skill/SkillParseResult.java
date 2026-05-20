package org.whitedoggy.mapleweb2.domain.skill;

import java.util.List;

public record SkillParseResult(
        List<String> effects,
        boolean lucidTransformSuspected
) {
}
