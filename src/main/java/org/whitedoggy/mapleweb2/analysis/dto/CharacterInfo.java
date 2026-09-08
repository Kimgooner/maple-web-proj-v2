package org.whitedoggy.mapleweb2.analysis.dto;

import org.whitedoggy.mapleweb2.domain.common.stat.GameData;

/**
 * @param mainStats 이 직업의 주스탯 (STR/DEX/INT/LUK). 화면이 남의 직업 스탯 증감을 지운다.
 * @param subStats  부스탯. 직업에 따라 둘 이상일 수 있다.
 * @param usesMagic 마력을 쓰는 직업인가. 공격력·마력 중 어느 쪽을 보여줄지 가른다.
 */
public record CharacterInfo(
        String name,
        String className,
        Integer level,
        String guild,
        String world,
        String image,
        java.util.List<String> mainStats,
        java.util.List<String> subStats,
        boolean usesMagic
) {
    /** 직업에서 따라오는 셋은 늘 같은 곳에서 온다. 세 군데가 따로 채우면 한 곳만 빠뜨린다. */
    public static CharacterInfo of(
            String name, String className, Integer level, String guild, String world, String image,
            GameData gameData) {
        return new CharacterInfo(name, className, level, guild, world, image,
                gameData.mainStats(className), gameData.subStats(className), gameData.isMageClass(className));
    }
}
