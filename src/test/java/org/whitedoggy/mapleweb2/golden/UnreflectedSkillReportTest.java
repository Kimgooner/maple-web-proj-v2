package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.domain.skill.ChallengersBuffs;
import org.whitedoggy.mapleweb2.domain.skill.PetBuffSkills;
import org.whitedoggy.mapleweb2.domain.skill.SkillRules;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 반영하지 않은 0차 스킬 중 전투 스탯 문구를 가진 것을 전부 남긴다.
 *
 * <p>있는 이유: {@code 달콤하담}·{@code 절세미호 펫의 버프}는 단일 펫 고유 버프라
 * 이름에 {@code Lv.N}이 없어 패턴에 걸리지 않았고, 조용히 버려지는 바람에 오랫동안 드러나지
 * 않았다. 새 펫이나 새 이벤트 버프가 같은 식으로 사라지지 않도록 목록을 상시 노출한다.
 *
 * <p>대부분은 <b>제외가 맞다</b>(소울웨폰 소환, 액티브 버프, 0차 창에 섞여 들어온 직업 스킬).
 * 이미 확인해 제외하기로 한 것은 {@link #REVIEWED}에 적어 목록에서 빼고, 남는 항목만 검토한다.
 *
 * <p>결과: {@code build/reports/golden/unreflected-skills.txt}
 */
@SpringBootTest
class UnreflectedSkillReportTest {

    private static final Path REPORT = Path.of("build/reports/golden/unreflected-skills.txt");
    private static final Pattern PET_SET = Pattern.compile("Lv\\.[123](?:\\s|$)");

    /** 전투 스탯 낱말. 하나라도 있으면 후보로 본다. */
    private static final Set<String> COMBAT = Set.of(
            "공격력", "마력", "올스탯", "ALLSTAT", "데미지", "보스 몬스터",
            "크리티컬 데미지", "최종 데미지", "STR", "DEX", "INT", "LUK");

    /** 확인 후 제외하기로 한 것. 사유를 함께 적어 다시 들여다보지 않게 한다. */
    private static final Map<String, String> REVIEWED = Map.of(
            "연합의 의지", "전투력 미반영 (확인 완료)",
            "고급 무기 제련", "소모품 사용 버프",
            "무기 제련", "소모품 사용 버프",
            "챔피언의 가호", "액티브 버프",
            "언다잉 엠버", "액티브 버프 (영웅의 메아리 계열)",
            "영웅의 메아리", "액티브 버프",
            "쓸만한 샤프 아이즈", "액티브 버프",
            "쓸만한 어드밴스드 블레스", "액티브 버프",
            "상급 장신구 강화", "소모품 사용 버프",
            "장신구 강화", "소모품 사용 버프");

    @Autowired private SkillRules rules;
    @Autowired private ChallengersBuffs challengersBuffs;
    @Autowired private PetBuffSkills petBuffSkills;
    @Autowired private ObjectMapper mapper;

    @Test
    void reportUnreflectedSkills() throws Exception {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> effects = new LinkedHashMap<>();

        FixtureLoader.forEach(mapper, fixture -> {
            JsonNode skills = fixture.snapshot().document(NexonEndpoint.SKILL_0).path("character_skill");
            for (JsonNode skill : skills) {
                String name = skill.path("skill_name").asText("");
                String effect = skill.path("skill_effect").asText("").replace('\n', ' ').trim();
                if (isReflected(name) || REVIEWED.containsKey(name)) {
                    continue;
                }
                if (COMBAT.stream().noneMatch(effect::contains)) {
                    continue;
                }
                counts.computeIfAbsent(name, k -> new int[1])[0]++;
                effects.putIfAbsent(name, effect);
            }
        });

        StringBuilder out = new StringBuilder();
        out.append("반영하지 않은 0차 스킬 중 전투 스탯 문구가 있는 것\n");
        out.append("확인 후 제외한 ").append(REVIEWED.size()).append("종은 목록에서 뺐다.\n");
        out.append("펫 버프가 새로 보이면 pet-buff-skills.yml 에, 이벤트 버프면 event-buffs.yml 에 추가한다.\n\n");
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(entry -> out.append(String.format("%5d명  %-26s %s%n",
                        entry.getValue()[0], entry.getKey(),
                        effects.get(entry.getKey()).substring(0,
                                Math.min(90, effects.get(entry.getKey()).length())))));

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, out.toString());
        System.out.println("[diagnostic] " + REPORT.toAbsolutePath());
    }

    private boolean isReflected(String name) {
        return rules.directSkills().contains(name)
                || rules.blessingSkills().contains(name)
                || petBuffSkills.names().contains(name)
                || challengersBuffs.effectsOf(name).size() > 0
                || PET_SET.matcher(name).find();
    }
}
