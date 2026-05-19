package org.whitedoggy.mapleweb2.domain.item.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BowNormalization {
    public List<String> buildNormalizedBow(String weaponType, String weaponName, Integer starForce, Integer addOption) {
        List<String> effects = new ArrayList<>();
        System.out.println(weaponType);
        System.out.println(weaponName);
        System.out.println(starForce);
        System.out.println(addOption);
        String set = "제네시스";
        int scroll = 0;
        if(weaponName.contains("도전자")) {
            set = "도전자";
        }

        if(weaponName.contains("앱솔랩스") || weaponName.contains("8형")) {
            set = "앱솔랩스";
            scroll = 81;
        }
        if(weaponName.contains("아케인셰이드") || weaponName.contains("9형")) {
            set = "아케인셰이드";
        }
        if(weaponName.contains("제네시스")) {
            set = "제네시스";
            scroll = 72;
        }
        if(weaponName.contains("데스티니")) {
            set = "데스티니";
            scroll = 72;
        }

        int attack = starForceTable.get(set)[starForce] + scroll;
        int add = buildAddOption(set, weaponType, addOption);

        effects.add("공격력 " + (attack + add));
        effects.add("마력 " + (attack + add));
        return effects;
    }

    private final Map<String, int[]> starForceTable = Map.ofEntries(
            Map.entry("도전자", new int[]{273, 279, 285, 291, 297, 303, 310, 317, 324, 331, 338, 345, 352, 360, 368, 376, 385, 394, 404, 415, 427, 440, 454, 454, 454, 454, 454, 454, 454, 454, 454}),
            Map.entry("앱솔랩스", new int[]{273, 279, 285, 291, 297, 303, 310, 317, 324, 331, 338, 345, 352, 360, 368, 376, 385, 394, 404, 415, 427, 440, 454, 454, 454, 454, 454, 454, 454, 454, 454}),
            Map.entry("아케인셰이드", new int[]{357, 365, 373, 381, 389, 397, 405, 414, 423, 432, 441, 450, 460, 470, 480, 490, 503, 516, 530, 544, 559, 575, 592, 592, 592, 592, 592, 592, 592, 592, 592}),
            Map.entry("제네시스", new int[]{564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564}),
            Map.entry("데스티니", new int[]{626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 626, 663, 701, 740, 740, 740, 740, 740, 740})
    );
    /*
    public List<String> buildNormalizedBow(String weaponType, String weaponName, Integer starForce, Integer addOption) {
        List<String> effects = new ArrayList<>();
        BuildOutput base = build(weaponName);

        int baseAttack = base.baseAttack;
        int scrollAttack = base.scrollAttack;
        int starForceAttack = buildStarForce(baseAttack + scrollAttack, safe(starForce), base.type);
        int addOptionAttack = buildAddOption(base.type, weaponType, addOption);

        System.out.println(weaponType + ", " + weaponName + ", " + starForce + ", " + addOption);
        System.out.println(baseAttack + ", " + scrollAttack + ", " + starForceAttack + ", " + addOptionAttack);
        System.out.println(baseAttack + scrollAttack + starForceAttack + addOptionAttack);

        int attack = base.baseAttack
                + base.scrollAttack
                + buildStarForce(base.baseAttack + base.scrollAttack, safe(starForce), base.type)
                + buildAddOption(base.type, weaponType, addOption);

        effects.add("공격력 " + attack);
        effects.add("마력 " + attack);
        return effects;
    }

    private final Map<String, int[]> starForceTable = Map.ofEntries(
            Map.entry("도전자", new int[]{9, 18, 28, 39, 51, 64, 78, 110, 143, 177, 177, 177, 177, 177, 177}),
            Map.entry("앱솔랩스", new int[]{9, 18, 28, 39, 51, 64, 78, 110, 143, 177, 177, 177, 177, 177, 177}),
            Map.entry("아케인셰이드", new int[]{13, 26, 40, 54, 69, 85, 102, 136, 171, 207, 244, 282, 321, 361, 402}),
            Map.entry("제네시스", new int[]{13, 26, 40, 54, 69, 85, 102, 136, 171, 207, 244, 282, 321, 361, 402}),
            Map.entry("데스티니", new int[]{16, 32, 49, 66, 84, 103, 123, 123, 123, 123, 123, 123, 123, 123, 123})
    );

    private record BuildOutput(int baseAttack, int scrollAttack, String type) {
    }

    private BuildOutput build(String weaponName) {
        if (weaponName.contains("도전자")) {
            return new BuildOutput(192, 81, "도전자");
        }
        if (weaponName.contains("앱솔랩스")) {
            return new BuildOutput(192, 81, "앱솔랩스");
        }
        if (weaponName.contains("아케인셰이드")) {
            return new BuildOutput(276, 81, "아케인셰이드");
        }
        if (weaponName.contains("제네시스")) {
            return new BuildOutput(318, 72, "제네시스");
        }
        if (weaponName.contains("데스티니")) {
            return new BuildOutput(349, 72, "데스티니");
        }
        return new BuildOutput(318, 72, "제네시스");
    }

    private int buildStarForce(int attack, int starForce, String type) {
        if (starForce <= 0) {
            return 0;
        }

        int addValue = Math.round(attack / 50.0f) + 1;
        if (starForce <= 15) {
            return addValue * starForce;
        }

        return addValue * 15 + starForceTable.get(type)[starForce - 16];
    }
    */
    private int buildAddOption(String weaponFamily, String weaponType, Integer addOption) {
        Integer stage = WeaponAddOptionTable.findStage(weaponFamily, weaponType, addOption);
        if (stage == null) {
            return 0;
        }
        return WeaponAddOptionTable.bowAddOption(weaponFamily, stage);
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
