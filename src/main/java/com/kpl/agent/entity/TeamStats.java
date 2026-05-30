package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 战队赛季统计：某赛事下战队的汇总数据 */
@Data
@TableName("team_stats")
public class TeamStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String leagueId;
    private String teamId;
    private String teamName;
    private String teamIcon;

    private Integer battleCount;
    private Double winRate;

    private Double avgKill;
    private Double avgDeath;
    private Double avgKda;
    private Double avgGold;

    /** 场均一血率 */
    private Double avgFirstBlood;

    /** 场均推塔 */
    private Double avgPushTower;

    /** 场均龙控制率 */
    private Double avgDragonControlRate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
