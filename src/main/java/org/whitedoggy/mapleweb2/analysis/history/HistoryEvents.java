package org.whitedoggy.mapleweb2.analysis.history;

import org.whitedoggy.mapleweb2.analysis.dto.CharacterInfo;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentPresetInfo;

import java.time.LocalDate;
import java.util.List;

/** SSE 로 내보내는 이벤트 본문들. 이벤트 이름은 {@link CombatPowerHistoryService} 가 붙인다. */
public final class HistoryEvents {

    private HistoryEvents() {
    }

    /**
     * 첫 이벤트. 총 몇 개를 조회할지와 캐릭터 정보를 먼저 알린다.
     * 잘림 여부는 캐릭터 생성일로 미리 정해지므로 여기서 이미 확정이다.
     */
    public record Meta(
            String ocid,
            String range,
            CharacterInfo characterInfo,
            /** 계산에 실제로 쓴 프리셋 번호. 화면이 "보스 프리셋 기준" 대신 이걸 적는다. */
            CurrentPresetInfo preset,
            int requestedCount,
            int plannedCount,
            boolean truncated,
            LocalDate truncatedFrom,
            List<LocalDate> dates,
            /** 이 순간 함께 추이를 받고 있는 사람 수. 자기 자신을 포함한다. */
            int concurrent
    ) {
    }

    /**
     * 지점 하나가 준비될 때마다. {@code index}/{@code total} 이 진행률이다.
     *
     * <p>{@code concurrent} 를 지점마다 실어 보내는 것은 기다리는 동안 값이 변하기 때문이다.
     * 따로 타이머를 두어 흘려보낼 수도 있지만, 지점은 어차피 계속 오므로 정수 하나를 얹는
     * 편이 스트림을 둘로 나누는 것보다 간단하다.
     *
     * @param index 1부터 센다
     */
    public record Point(int index, int total, CombatPowerHistoryPoint point, int concurrent) {
    }

    /** 마지막 이벤트. */
    public record Done(int loadedCount, boolean truncated, LocalDate truncatedFrom) {
    }

    /**
     * 실패. 스트림은 이 이벤트를 끝으로 정상 종료한다.
     *
     * @param code {@code NOT_FOUND} 면 없는 캐릭터라 화면이 따로 그린다. 그 밖은 {@code ERROR}.
     *             문구를 문자열로 비교하지 않도록 코드를 따로 준다.
     */
    public record Error(String code, String message) {
    }
}
