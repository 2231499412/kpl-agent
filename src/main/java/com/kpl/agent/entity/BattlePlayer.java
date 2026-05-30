package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 对局选手数据：每个选手在某局的表现 */
@Data
@TableName("battle_player")
public class BattlePlayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String battleId;
    private String teamId;
    private String teamName;
    private String playerName;

    private Integer heroId;
    private String heroName;

    /** 阵营：1/2 */
    private Integer camp;

    private Integer killNum;
    private Integer deathNum;
    private Integer assistNum;
    private Integer gold;

    /** 总伤害 */
    private Long hurtTotal;

    /** 对英雄伤害 */
    private Long hurtToHero;

    /** 总承伤 */
    private Long beHurtTotal;

    private Double kda;
    private Double mvpScore;
    private Integer isMvp;

    /** 参团率 */
    private Double participationRate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
