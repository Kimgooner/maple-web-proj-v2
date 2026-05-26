package org.whitedoggy.mapleweb2.analysis.dto;

import org.whitedoggy.mapleweb2.analysis.data.DataSheet;

import java.time.LocalDate;

public record DataSheetResponse(
        //DataSheet CurrentPresetDataSheet,
        //기본 정보
        String ocid,
        LocalDate date,

        //캐릭터 정보
        String characterName,
        String characterClass,
        Integer characterLevel,
        String characterGuild,
        String characterWorld,

        //캐릭터 이미지
        String characterImage,

        //데이터 시트
        DataSheet dataSheet
) {
}
