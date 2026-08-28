package org.whitedoggy.mapleweb2.domain.item.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 무기 공격력을 활 기준으로 정규화한다.
 *
 * <p>전투력 공식은 무기 종류를 가리지 않으므로, 무기가 실제로 가진 공격력 대신
 * (세트 스타포스 표) + (주문서 작) + (추가옵션을 활 기준으로 환산한 값)을 쓴다.
 * 표와 상수는 전부 {@code game-data.yml}에 있다.
 *
 * <p>주문서 작은 세트 상수가 아니라 <b>아이템의 실제 작 횟수</b>로 계산한다.
 * 8작과 9작은 기본 공격력이 9, 스타포스 옵션이 3 차이 나므로 합계 12가 벌어진다.
 *
 * <p>검증: 아케인셰이드 보우 22성 = 592 + 170 = 762 (item_total_option 실측치와 일치),
 * 제네시스 듀얼보우건 22성 = 564 + 72 + 196 = 832 (실측치와 일치).
 *
 * <p><b>태도·대검(제로)은 예외다.</b> 다른 무기는 "같은 추 단계의 활 값"을 그대로 쓰면
 * 맞지만 태도는 그 대응이 성립하지 않는다(필요값 179가 활 사다리에 아예 없다). 전용 표는
 * {@code game-data.yml}의 {@code zero-bow-add-option}에 있고, 표본이 없는 단계는 비어 있어
 * {@code stageResolved}가 false가 된다.
 */
@Component
@RequiredArgsConstructor
public class BowNormalization {

    private final WeaponData weaponData;

    /**
     * @param effects      공격력·마력 효과 문자열
     * @param stageResolved 추가옵션이 몇 추인지 표에서 찾았는가.
     *                      못 찾으면 활 환산값 없이 계산되므로(수백의 공격력이 사라진다)
     *                      호출부가 이를 알 수 있어야 한다.
     */
    public record NormalizedWeapon(List<String> effects, boolean stageResolved) {
    }

    public NormalizedWeapon normalize(String weaponPart, String weaponName,
                                      Integer starForce, Integer addOption, Integer scrollUpgrade) {
        WeaponData.WeaponSet set = weaponData.resolveSet(weaponName);

        Integer stage = weaponData.findStage(set.name(), weaponPart, addOption);
        int attack = weaponData.starForceAttack(set.name(), safe(starForce))
                + weaponData.scrollAttack(set, scrollUpgrade);

        // 태도·대검(제로)은 "같은 단계의 활 값" 대응이 성립하지 않아 전용 표를 쓴다.
        int add;
        boolean resolved;
        if (weaponData.usesZeroBowAddOption(weaponPart)) {
            Integer zero = weaponData.zeroBowAddOptionOf(set.name(), stage);
            add = zero == null ? 0 : zero;
            resolved = zero != null;
        } else {
            add = weaponData.bowAddOption(set.name(), stage);
            resolved = stage != null;
        }

        List<String> effects = new ArrayList<>();
        // 직업에 따라 둘 중 하나만 쓰인다. 어느 쪽인지는 여기서 판단하지 않는다.
        effects.add("공격력 " + (attack + add));
        effects.add("마력 " + (attack + add));
        return new NormalizedWeapon(effects, resolved);
    }

    public List<String> buildNormalizedBow(String weaponPart, String weaponName,
                                           Integer starForce, Integer addOption, Integer scrollUpgrade) {
        return normalize(weaponPart, weaponName, starForce, addOption, scrollUpgrade).effects();
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
