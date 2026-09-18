package org.whitedoggy.mapleweb2.analysis.data;

import org.junit.jupiter.api.Test;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataSheetContributionsTest {

    private static StatSheet sheet(String name, int attack, double boss) {
        StatSheet s = new StatSheet(name);
        s.setATTACK_POWER(attack);
        s.setBOSS_DAMAGE(boss);
        return s;
    }

    private static ItemSnapShot item(int attack) {
        ItemSnapShot item = new ItemSnapShot("x", "");
        item.setStatSheet(sheet("x", attack, 0));
        return item;
    }

    private static DataSheet sample() {
        DataSheet d = new DataSheet();
        Map<String, ItemSnapShot> items = new LinkedHashMap<>();
        items.put("장비 - 무기", item(300));
        items.put("장비 - 모자", item(10));
        d.setItemEquip(items);
        d.setCashEquip(Map.of());
        d.setPetEquip(Map.of());
        d.setSetEffect(sheet("setEffect", 20, 30));
        d.setSkill(sheet("skill", 40, 0));
        d.setHexaStat(new StatSheet("hexaStat"));
        d.setSymbol(new StatSheet("symbol"));
        d.setHyperStat(new StatSheet("hyperStat"));
        d.setAbility(new StatSheet("ability"));
        d.setOtherStat(new StatSheet("otherStat"));
        d.setConsumableItem(new StatSheet("consumableItem"));
        d.setAbilityPoint(new StatSheet("abilityPoint"));
        d.setUnionArtifact(new StatSheet("unionArtifact"));
        d.setUnionChampion(new StatSheet("unionChampion"));
        d.setUnionOccupied(new StatSheet("unionOccupied"));
        d.setUnionRaider(new StatSheet("unionRaider"));
        d.buildSum();
        return d;
    }

    /** 소스별 몫을 다 더하면 종합이다 — 화면의 "요소별 합 → 총합"이 실제 종합과 같은 수라야 한다. */
    @Test
    void 소스별_몫의_합은_종합이다() {
        DataSheet d = sample();
        Map<String, StatSheet> parts = d.contributions();
        assertThat(parts.keySet()).containsExactly("items", "setEffect", "skill");
        assertThat(parts.get("items").getATTACK_POWER()).isEqualTo(310);
        StatSheet sum = new StatSheet("합");
        parts.values().forEach(sum::merge);
        assertThat(sum.getATTACK_POWER()).isEqualTo(d.getSumSheet().getATTACK_POWER());
        assertThat(sum.getBOSS_DAMAGE()).isEqualTo(d.getSumSheet().getBOSS_DAMAGE());
    }

    /** 한 소스를 뺀 사본은 그 몫만 빠지고 원본은 그대로다. */
    @Test
    void 소스를_빼면_그만큼만_빠지고_원본은_그대로다() {
        DataSheet d = sample();
        DataSheet withoutSet = d.without("setEffect");
        assertThat(withoutSet.getSumSheet().getATTACK_POWER()).isEqualTo(370 - 20);
        assertThat(withoutSet.getSumSheet().getBOSS_DAMAGE()).isEqualTo(0.0);
        assertThat(d.getSumSheet().getATTACK_POWER()).isEqualTo(370);

        DataSheet withoutItems = d.without("items");
        assertThat(withoutItems.getSumSheet().getATTACK_POWER()).isEqualTo(60);
        assertThat(d.getItemEquip()).hasSize(2);
    }
}
