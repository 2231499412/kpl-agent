package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 比赛实体：对应 match 表，表示两支队伍的一场BO系列赛 */
@Data
@TableName("`match`")
public class Match {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String matchId;
    private String leagueId;

    /** 队伍1 */
    private String camp1TeamId;
    private String camp1TeamName;
    private Integer camp1Score;

    /** 队伍2 */
    private String camp2TeamId;
    private String camp2TeamName;
    private Integer camp2Score;

    /** BO数 */
    private Integer bo;

    /** 获胜方：1=队伍1 2=队伍2 */
    private Integer winCamp;

    /** 状态：0未开始 1进行中 2已结束 */
    private Integer status;

    /** 赛段：cgs1/cgs2/cgs3/jhs/zjs */
    private String matchStage;
    private String matchStageDesc;

    private LocalDateTime startTime;
    private String matchAddress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
