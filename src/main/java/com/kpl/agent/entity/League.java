package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 赛事实体：对应 league 表 */
@Data
@TableName("league")
public class League {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 赛事ID，如 20260001 */
    private String leagueId;

    /** 赛事名称，如 "2026年KPL春季赛" */
    private String leagueName;

    /** 赛事类型：kpl / world_champion_cup / winter_champion_cup */
    private String leagueType;

    /** 年份 */
    private Integer year;

    /** 赛季：1春/2夏/3年度总决赛/4挑战者杯 */
    private Integer season;

    /** 内部赛事ID，如 KPL2026S1 */
    private String ccLeagueId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 状态：1进行中 2已结束 */
    private Integer status;

    private String leagueIcon;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
