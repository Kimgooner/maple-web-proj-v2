package org.whitedoggy.mapleweb2.external.nexon.config;

public enum NexonEndpoint {
    BASIC("/maplestory/v1/character/basic"),
    STAT("/maplestory/v1/character/stat"),
    ITEM_EQUIPMENT("/maplestory/v1/character/item-equipment"),
    CASH_ITEM_EQUIPMENT("/maplestory/v1/character/cashitem-equipment"),
    SET_EFFECT("/maplestory/v1/character/set-effect"),
    SYMBOL_EQUIPMENT("/maplestory/v1/character/symbol-equipment"),
    PET_EQUIPMENT("/maplestory/v1/character/pet-equipment"),
    HYPER_STAT("/maplestory/v1/character/hyper-stat"),
    ABILITY("/maplestory/v1/character/ability"),
    SKILL_0("/maplestory/v1/character/skill"),
    HEXA_MATRIX_STAT("/maplestory/v1/character/hexamatrix-stat"),
    HEXA_MATRIX("/maplestory/v1/character/hexamatrix"),
    OTHER_STAT("/maplestory/v1/character/other-stat"),
    UNION_RAIDER("/maplestory/v1/user/union-raider"),
    UNION_CHAMPION("/maplestory/v1/user/union-champion"),
    UNION_ARTIFACT("/maplestory/v1/user/union-artifact");

    private final String path;
    NexonEndpoint(String path) {
        this.path = path;
    }
    public String path() {
        return path;
    }
}
