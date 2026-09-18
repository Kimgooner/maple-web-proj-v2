package org.whitedoggy.mapleweb2.analysis.history;

import org.whitedoggy.mapleweb2.analysis.dto.CharacterInfo;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentPresetInfo;

import java.time.LocalDate;

/**
 * 오늘치 스냅샷에서 뽑아 둔 것들. 조회 한 번의 <b>앞머리</b>다.
 *
 * <p>추이 조회는 지나간 날짜를 전부 캐시에서 꺼내 오더라도 오늘치만은 매번 새로 받았다.
 * 오늘은 계속 변해서 캐시하지 않기 때문인데, 그 한 번이 넥슨 호출 17회다. 캐시가 다 맞은
 * 요청도 17회를 쓰니, 사람이 몰리면 그것만으로 초당 한도를 밀어낸다.
 *
 * <p>그래서 스냅샷 자체(400KB 넘는다) 대신 <b>거기서 실제로 쓰는 것만</b> 짧게 들고 있는다.
 * 5분이면 넥슨의 반영 지연(평균 15분)보다 짧아 더 낡은 값을 보여줄 일이 없고, 그 사이
 * 같은 캐릭터를 다시 열거나 30일 ↔ 12개월을 오가는 동안에는 호출이 아예 없다.
 *
 * @param createdAt 캐릭터 생성일. 그 이전 구간을 잘라 내는 데 쓴다. 모르면 null
 * @param today     오늘 지점. 계산이 터졌으면 null 이고, 그때는 캐시하지 않는다
 */
public record TodayHead(
        CharacterInfo characterInfo,
        CurrentPresetInfo preset,
        LocalDate createdAt,
        CombatPowerHistoryPoint today
) {
}
