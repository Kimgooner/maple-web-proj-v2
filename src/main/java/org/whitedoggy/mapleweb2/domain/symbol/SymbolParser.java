package org.whitedoggy.mapleweb2.domain.symbol;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 아케인·어센틱 심볼의 스탯을 읽는다.
 *
 * <p>심볼은 "소지한 자와 공명하여 해당 캐릭터에 맞는 주스탯을 증가"시킨다. 그래서
 * 응답의 STR/DEX/INT/LUK 중 주스탯 하나에만 값이 있는 것이 정상이다.
 *
 * <p>그런데 <b>모험가 해적(캡틴·바이퍼)에서 STR과 DEX로 쪼개져 오는 캐릭터가 있다.</b>
 * 심볼을 키우던 시점의 주스탯이 그대로 굳어 남은 것으로 보이며, 지역 순으로 늘어놓으면
 * 어느 시점을 경계로 STR↔DEX가 갈린다. 합계는 정상이고(아케인 2200 / 어센틱 2500),
 * 게임은 그 합계를 <b>전부 현재 주스탯</b>으로 친다 — 심볼 STR 16,200을 가진 캡틴의
 * 스탯창 STR이 9,750에 그친다. 표본 기준 캡틴 30명 중 4명, 바이퍼 20명 중 1명.
 *
 * <p>그래서 네 스탯을 주스탯 하나로 합친다. 예외는 <b>제논</b>(STR/DEX/LUK을 모두
 * 주스탯으로 쓴다)과 <b>데몬어벤져</b>(HP 기반)로, 이들은 원래 값을 그대로 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class SymbolParser {

    private static final Set<String> MERGEABLE = Set.of("STR", "DEX", "INT", "LUK");

    private final GameData gameData;

    public List<String> getSymbolStatEffects(JsonNode symbolEquipment) {
        String mainStat = mainStatOf(symbolEquipment.path("character_class").asText(""));

        List<String> effects = new ArrayList<>();
        int merged = 0;
        for (JsonNode symbol : symbolEquipment.path("symbol")) {
            int str = symbol.path("symbol_str").asInt(0);
            int dex = symbol.path("symbol_dex").asInt(0);
            int intel = symbol.path("symbol_int").asInt(0);
            int luk = symbol.path("symbol_luk").asInt(0);
            if (mainStat == null) {
                append(effects, "STR", str);
                append(effects, "DEX", dex);
                append(effects, "INT", intel);
                append(effects, "LUK", luk);
            } else {
                merged += str + dex + intel + luk;
            }
            append(effects, "HP", symbol.path("symbol_hp").asInt(0));
        }
        if (mainStat != null) {
            append(effects, mainStat, merged);
        }
        return effects;
    }

    /** 네 스탯을 합쳐 넣을 주스탯. 합치면 안 되는 직업이면 {@code null}. */
    private String mainStatOf(String characterClass) {
        if (characterClass == null || characterClass.isBlank()) {
            return null;
        }
        List<String> mains;
        try {
            mains = gameData.mainStats(characterClass);
        } catch (RuntimeException unknownClass) {
            return null;
        }
        if (mains.size() != 1 || !MERGEABLE.contains(mains.getFirst())) {
            return null;
        }
        return mains.getFirst();
    }

    private void append(List<String> effects, String statName, int value) {
        if (value > 0) {
            effects.add(statName + " " + value + " 증가");
        }
    }
}
