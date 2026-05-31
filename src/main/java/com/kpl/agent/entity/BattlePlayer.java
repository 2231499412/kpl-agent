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

    /** 召唤师技能ID */
    private Integer summonerAbilityId;
    /** 召唤师技能名称 */
    private String summonerAbilityName;

    /** 伤害占比 */
    private Double hurtTotalRate;
    /** 承伤占比 */
    private Double beHurtTotalRate;
    /** 对英雄总伤害 */
    private Long hurtToHeroTotal;
    /** 被英雄总伤害 */
    private Long beHurtByHeroTotal;
    /** 对英雄伤害占比 */
    private Double hurtToHeroTotalRate;
    /** 被英雄伤害占比 */
    private Double beHurtByHeroTotalRate;

    /** 位置编号 */
    private Integer position;
    /** 位置描述 */
    private String positionDesc;
    /** 是否败方MVP */
    private Integer isLoseMvp;
    /** 铭文ID列表 */
    private String symbolIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
