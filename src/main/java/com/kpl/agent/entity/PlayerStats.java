package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 选手赛季统计：某赛事下选手的汇总数据 */
@Data
@TableName("player_stats")
public class PlayerStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String leagueId;
    private String playerName;
    private String playerIcon;
    private String teamName;

    /** 位置编码：2中路/4打野/5对抗路/7发育路/6游走 */
    private Integer position;

    /** 位置描述：中路/打野/对抗路/发育路/游走 */
    private String positionDesc;

    private Integer battleCount;
    private Double winRate;

    private Double avgKill;
    private Double avgDeath;
    private Double avgAssist;
    private Double avgKda;
    private Double avgGold;

    private Double avgHurtToHero;
    private Double avgBeHurt;
    private Double avgParticipationRate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
