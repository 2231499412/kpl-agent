package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 英雄赛季统计：某赛事下英雄的汇总数据 */
@Data
@TableName("hero_stats")
public class HeroStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String leagueId;
    private Integer heroId;
    private String heroName;
    private String heroIcon;

    private Integer battleCount;
    private Double winRate;

    private Double avgKill;
    private Double avgDeath;
    private Double avgAssist;
    private Double avgKda;
    private Double avgGold;

    private Integer banNum;
    private Double banRate;
    private Integer pickNum;
    private Double pickRate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
