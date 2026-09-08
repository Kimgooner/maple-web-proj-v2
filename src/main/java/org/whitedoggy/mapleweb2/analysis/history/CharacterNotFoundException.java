package org.whitedoggy.mapleweb2.analysis.history;

/**
 * 그런 이름의 캐릭터가 없다. 화면이 "없는 캐릭터"로 따로 그려야 해서 다른 실패와 구분한다.
 *
 * <p>넥슨은 없는 이름에 400 을 주는데, 그 응답을 그대로 흘리면 업스트림 URL 이 새어 나간다.
 */
public class CharacterNotFoundException extends RuntimeException {
    public CharacterNotFoundException(String characterName) {
        super("캐릭터를 찾을 수 없습니다: " + characterName);
    }
}
