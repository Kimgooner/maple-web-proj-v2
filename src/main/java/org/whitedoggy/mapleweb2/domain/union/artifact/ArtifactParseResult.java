package org.whitedoggy.mapleweb2.domain.union.artifact;

import java.util.List;

/**
 * 아티팩트 파싱 결과.
 *
 * @param effects        전투력에 반영할 효과 문구
 * @param expiredCrystals 유효기간이 지나 계산에서 제외한 크리스탈 수.
 *                        게임에 접속하지 않으면 API의 {@code validity_flag}가 아직 0일 수 있어
 *                        만료일로 직접 판정한다. 이 값이 0보다 크면 곧 전투력이 떨어질 상태다.
 */
public record ArtifactParseResult(List<String> effects, int expiredCrystals) {
}
