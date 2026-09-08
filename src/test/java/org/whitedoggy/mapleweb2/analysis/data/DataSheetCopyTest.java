package org.whitedoggy.mapleweb2.analysis.data;

import org.junit.jupiter.api.Test;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataSheet#copy(DataSheet)} 는 손으로 관리하는 필드 목록이다. 필드를 새로 넣고
 * 여기 넣는 걸 잊으면 그 값만 조용히 빠진 채 계산되므로, 리플렉션으로 강제한다.
 *
 * <p>일부러 안 옮기는 필드는 {@link #NOT_COPIED} 에 적는다. 목록이 실제와 어긋나도
 * 실패하므로, 옮기기 시작했는데 목록을 안 고치는 경우도 걸린다.
 */
class DataSheetCopyTest {

    /**
     * {@code copy()} 뒤에 파이프라인이 따로 세팅하는 값들이라 옮기지 않는다.
     * {@code DataSheetService.getDataSheet} 가 전투력을 계산해 넣고,
     * {@code applyPirateBlessIfBetter} 가 파이렛 블레스 적용 여부를 정한다.
     */
    private static final Set<String> NOT_COPIED = Set.of("combatPower", "pirateBlessApplied");

    @Test
    void copyMovesEveryFieldOrSaysWhyNot() throws Exception {
        DataSheet source = new DataSheet();
        DataSheet target = new DataSheet();
        Map<String, Object> planted = new LinkedHashMap<>();

        for (Field field : instanceFields()) {
            Object value = distinctValue(field);
            field.set(source, value);
            planted.put(field.getName(), value);
        }

        target.copy(source);

        List<String> problems = new ArrayList<>();
        for (Field field : instanceFields()) {
            boolean moved = Objects.equals(field.get(target), planted.get(field.getName()));
            if (NOT_COPIED.contains(field.getName())) {
                if (moved) {
                    problems.add(field.getName() + ": 이제 copy() 가 옮긴다 — NOT_COPIED 에서 빼라");
                }
            } else if (!moved) {
                problems.add(field.getName() + ": copy() 가 옮기지 않는다 — copy() 에 넣거나 NOT_COPIED 에 적어라");
            }
        }

        assertTrue(problems.isEmpty(), "DataSheet.copy() 와 필드 목록이 어긋났다:\n  " + String.join("\n  ", problems));
    }

    private static List<Field> instanceFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : DataSheet.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    /** 기본값과 확실히 다른 값. 새 타입의 필드가 생기면 여기서 실패해 알려 준다. */
    private static Object distinctValue(Field field) {
        Class<?> type = field.getType();
        if (type == boolean.class) {
            return true;
        }
        if (type == int.class) {
            return 7;
        }
        if (type == Long.class) {
            return 1234L;
        }
        if (type == StatSheet.class) {
            return new StatSheet(field.getName());
        }
        if (type == Map.class) {
            return Map.of(field.getName(), field.getName());
        }
        throw new IllegalStateException(
                "이 타입에 쓸 값을 정해야 한다: " + type.getSimpleName() + " " + field.getName());
    }
}
