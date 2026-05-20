package org.whitedoggy.mapleweb2.domain.common.stat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatSheet {
    private String sheetName = null;
    private int STR = 0;
    private int DEX = 0;
    private int INT = 0;
    private int LUK = 0;
    private int HP = 0;
    private int ALL_STAT = 0;

    private int STR_PER_LEVEL9 = 0;
    private int DEX_PER_LEVEL9 = 0;
    private int INT_PER_LEVEL9 = 0;
    private int LUK_PER_LEVEL9 = 0;

    private int STR_NO_PERCENT = 0;
    private int DEX_NO_PERCENT = 0;
    private int INT_NO_PERCENT = 0;
    private int LUK_NO_PERCENT = 0;
    private int HP_NO_PERCENT = 0;
    private int ALL_STAT_NO_PERCENT = 0;

    private int ATTACK_POWER = 0;
    private int MAGIC_POWER = 0;

    private int STR_PERCENT = 0;
    private int DEX_PERCENT = 0;
    private int INT_PERCENT = 0;
    private int LUK_PERCENT = 0;
    private int HP_PERCENT = 0;
    private int ALL_STAT_PERCENT = 0;

    private int ATTACK_POWER_PERCENT = 0;
    private int MAGIC_POWER_PERCENT = 0;

    private int DAMAGE = 0;
    private int BOSS_DAMAGE = 0;
    private double CRITICAL_DAMAGE = 0.0;
    private double FINAL_DAMAGE = 0.0;

    public StatSheet(String name){
        this.sheetName = name;
    }

    public StatSheet plus(StatSheet other) {
        StatSheet result = new StatSheet(this.sheetName);

        result.STR = this.STR + other.STR;
        result.DEX = this.DEX + other.DEX;
        result.INT = this.INT + other.INT;
        result.LUK = this.LUK + other.LUK;
        result.HP = this.HP + other.HP;
        result.ALL_STAT = this.ALL_STAT + other.ALL_STAT;

        result.STR_PER_LEVEL9 = this.STR_PER_LEVEL9 + other.STR_PER_LEVEL9;
        result.DEX_PER_LEVEL9 = this.DEX_PER_LEVEL9 + other.DEX_PER_LEVEL9;
        result.INT_PER_LEVEL9 = this.INT_PER_LEVEL9 + other.INT_PER_LEVEL9;
        result.LUK_PER_LEVEL9 = this.LUK_PER_LEVEL9 + other.LUK_PER_LEVEL9;

        result.STR_NO_PERCENT = this.STR_NO_PERCENT + other.STR_NO_PERCENT;
        result.DEX_NO_PERCENT = this.DEX_NO_PERCENT + other.DEX_NO_PERCENT;
        result.INT_NO_PERCENT = this.INT_NO_PERCENT + other.INT_NO_PERCENT;
        result.LUK_NO_PERCENT = this.LUK_NO_PERCENT + other.LUK_NO_PERCENT;
        result.HP_NO_PERCENT = this.HP_NO_PERCENT + other.HP_NO_PERCENT;
        result.ALL_STAT_NO_PERCENT = this.ALL_STAT_NO_PERCENT + other.ALL_STAT_NO_PERCENT;

        result.STR_PERCENT = this.STR_PERCENT + other.STR_PERCENT;
        result.DEX_PERCENT = this.DEX_PERCENT + other.DEX_PERCENT;
        result.INT_PERCENT = this.INT_PERCENT + other.INT_PERCENT;
        result.LUK_PERCENT = this.LUK_PERCENT + other.LUK_PERCENT;
        result.HP_PERCENT = this.HP_PERCENT + other.HP_PERCENT;
        result.ALL_STAT_PERCENT = this.ALL_STAT_PERCENT + other.ALL_STAT_PERCENT;

        result.ATTACK_POWER = this.ATTACK_POWER + other.ATTACK_POWER;
        result.MAGIC_POWER = this.MAGIC_POWER + other.MAGIC_POWER;
        result.ATTACK_POWER_PERCENT = this.ATTACK_POWER_PERCENT + other.ATTACK_POWER_PERCENT;
        result.MAGIC_POWER_PERCENT = this.MAGIC_POWER_PERCENT + other.MAGIC_POWER_PERCENT;

        result.DAMAGE = this.DAMAGE + other.DAMAGE;
        result.BOSS_DAMAGE = this.BOSS_DAMAGE + other.BOSS_DAMAGE;
        result.CRITICAL_DAMAGE = this.CRITICAL_DAMAGE + other.CRITICAL_DAMAGE;
        result.FINAL_DAMAGE = this.FINAL_DAMAGE + other.FINAL_DAMAGE;

        return result;
    }

    public StatSheet minus(StatSheet other) {
        StatSheet result = new StatSheet(this.sheetName);

        result.STR = this.STR - other.STR;
        result.DEX = this.DEX - other.DEX;
        result.INT = this.INT - other.INT;
        result.LUK = this.LUK - other.LUK;
        result.HP = this.HP - other.HP;
        result.ALL_STAT = this.ALL_STAT - other.ALL_STAT;

        result.STR_PER_LEVEL9 = this.STR_PER_LEVEL9 - other.STR_PER_LEVEL9;
        result.DEX_PER_LEVEL9 = this.DEX_PER_LEVEL9 - other.DEX_PER_LEVEL9;
        result.INT_PER_LEVEL9 = this.INT_PER_LEVEL9 - other.INT_PER_LEVEL9;
        result.LUK_PER_LEVEL9 = this.LUK_PER_LEVEL9 - other.LUK_PER_LEVEL9;

        result.STR_NO_PERCENT = this.STR_NO_PERCENT - other.STR_NO_PERCENT;
        result.DEX_NO_PERCENT = this.DEX_NO_PERCENT - other.DEX_NO_PERCENT;
        result.INT_NO_PERCENT = this.INT_NO_PERCENT - other.INT_NO_PERCENT;
        result.LUK_NO_PERCENT = this.LUK_NO_PERCENT - other.LUK_NO_PERCENT;
        result.HP_NO_PERCENT = this.HP_NO_PERCENT - other.HP_NO_PERCENT;
        result.ALL_STAT_NO_PERCENT = this.ALL_STAT_NO_PERCENT - other.ALL_STAT_NO_PERCENT;

        result.STR_PERCENT = this.STR_PERCENT - other.STR_PERCENT;
        result.DEX_PERCENT = this.DEX_PERCENT - other.DEX_PERCENT;
        result.INT_PERCENT = this.INT_PERCENT - other.INT_PERCENT;
        result.LUK_PERCENT = this.LUK_PERCENT - other.LUK_PERCENT;
        result.HP_PERCENT = this.HP_PERCENT - other.HP_PERCENT;
        result.ALL_STAT_PERCENT = this.ALL_STAT_PERCENT - other.ALL_STAT_PERCENT;

        result.ATTACK_POWER = this.ATTACK_POWER - other.ATTACK_POWER;
        result.MAGIC_POWER = this.MAGIC_POWER - other.MAGIC_POWER;
        result.ATTACK_POWER_PERCENT = this.ATTACK_POWER_PERCENT - other.ATTACK_POWER_PERCENT;
        result.MAGIC_POWER_PERCENT = this.MAGIC_POWER_PERCENT - other.MAGIC_POWER_PERCENT;

        result.DAMAGE = this.DAMAGE - other.DAMAGE;
        result.BOSS_DAMAGE = this.BOSS_DAMAGE - other.BOSS_DAMAGE;
        result.CRITICAL_DAMAGE = this.CRITICAL_DAMAGE - other.CRITICAL_DAMAGE;
        result.FINAL_DAMAGE = this.FINAL_DAMAGE - other.FINAL_DAMAGE;

        return result;
    }

    public void merge(StatSheet other) {
        this.STR += other.STR;
        this.DEX += other.DEX;
        this.INT += other.INT;
        this.LUK += other.LUK;
        this.HP += other.HP;
        this.ALL_STAT += other.ALL_STAT;

        this.STR_PER_LEVEL9 += other.STR_PER_LEVEL9;
        this.DEX_PER_LEVEL9 += other.DEX_PER_LEVEL9;
        this.INT_PER_LEVEL9 += other.INT_PER_LEVEL9;
        this.LUK_PER_LEVEL9 += other.LUK_PER_LEVEL9;

        this.STR_NO_PERCENT += other.STR_NO_PERCENT;
        this.DEX_NO_PERCENT += other.DEX_NO_PERCENT;
        this.INT_NO_PERCENT += other.INT_NO_PERCENT;
        this.LUK_NO_PERCENT += other.LUK_NO_PERCENT;
        this.HP_NO_PERCENT += other.HP_NO_PERCENT;
        this.ALL_STAT_NO_PERCENT += other.ALL_STAT_NO_PERCENT;

        this.STR_PERCENT += other.STR_PERCENT;
        this.DEX_PERCENT += other.DEX_PERCENT;
        this.INT_PERCENT += other.INT_PERCENT;
        this.LUK_PERCENT += other.LUK_PERCENT;
        this.HP_PERCENT += other.HP_PERCENT;
        this.ALL_STAT_PERCENT += other.ALL_STAT_PERCENT;

        this.ATTACK_POWER += other.ATTACK_POWER;
        this.MAGIC_POWER += other.MAGIC_POWER;
        this.ATTACK_POWER_PERCENT += other.ATTACK_POWER_PERCENT;
        this.MAGIC_POWER_PERCENT += other.MAGIC_POWER_PERCENT;

        this.DAMAGE += other.DAMAGE;
        this.BOSS_DAMAGE += other.BOSS_DAMAGE;
        this.CRITICAL_DAMAGE += other.CRITICAL_DAMAGE;
        this.FINAL_DAMAGE += other.FINAL_DAMAGE;
    }

    public boolean isZero() {
        return STR == 0
                && DEX == 0
                && INT == 0
                && LUK == 0
                && HP == 0
                && ALL_STAT == 0
                && STR_PER_LEVEL9 == 0
                && DEX_PER_LEVEL9 == 0
                && INT_PER_LEVEL9 == 0
                && LUK_PER_LEVEL9 == 0
                && STR_NO_PERCENT == 0
                && DEX_NO_PERCENT == 0
                && INT_NO_PERCENT == 0
                && LUK_NO_PERCENT == 0
                && HP_NO_PERCENT == 0
                && ALL_STAT_NO_PERCENT == 0
                && ATTACK_POWER == 0
                && MAGIC_POWER == 0
                && STR_PERCENT == 0
                && DEX_PERCENT == 0
                && INT_PERCENT == 0
                && LUK_PERCENT == 0
                && HP_PERCENT == 0
                && ALL_STAT_PERCENT == 0
                && ATTACK_POWER_PERCENT == 0
                && MAGIC_POWER_PERCENT == 0
                && DAMAGE == 0
                && BOSS_DAMAGE == 0
                && Double.compare(CRITICAL_DAMAGE, 0.0) == 0
                && Double.compare(FINAL_DAMAGE, 0.0) == 0;
    }
}
