package org.whitedoggy.mapleweb2.analysis.history;

/**
 * 캐릭터는 있는데 레벨이 낮아 계산 대상이 아니다.
 *
 * <p>계산은 4차 전직명을 기준으로 직업을 가른다. 그 아래 레벨은 전직명이 달라 직업을 못
 * 알아보고, 값이 나와도 믿을 수 없다. 이 서비스가 260 이상을 보는 것이라 조용히 틀린 값을
 * 주느니 왜 못 보는지 말하는 쪽이 낫다.
 *
 * <p>"없는 캐릭터"와 구분해서 던진다 — 사용자가 할 일이 다르다. 하나는 철자를 고치는 것이고
 * 다른 하나는 기다리는 것이다.
 */
public class CharacterTooLowException extends RuntimeException {
    /** 이 아래로는 조회하지 않는다. */
    public static final int MINIMUM_LEVEL = 260;

    private final Integer level;

    public CharacterTooLowException(String characterName, Integer level) {
        super(characterName + " 은(는) Lv." + (level == null ? "?" : level)
                + " 입니다. 이 서비스는 Lv." + MINIMUM_LEVEL + " 이상만 계산합니다.");
        this.level = level;
    }

    public Integer level() {
        return level;
    }
}
