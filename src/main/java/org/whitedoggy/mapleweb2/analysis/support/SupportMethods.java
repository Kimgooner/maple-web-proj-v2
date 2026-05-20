package org.whitedoggy.mapleweb2.analysis.support;

import lombok.RequiredArgsConstructor;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.List;

import static org.whitedoggy.mapleweb2.domain.common.stat.JobStatTable.JOBS;

@RequiredArgsConstructor
public class SupportMethods {
    //methods
    public static List<String> getMainStat(String characterClass){
        return JOBS.get(characterClass).mainStats();
    }
    public static List<String> getSubStat(String characterClass){
        return JOBS.get(characterClass).subStats();
    }
}
