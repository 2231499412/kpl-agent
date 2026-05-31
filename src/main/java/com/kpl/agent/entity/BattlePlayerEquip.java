package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 对局选手装备数据 */
@Data
@TableName("battle_player_equip")
public class BattlePlayerEquip {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String battleId;
    private String leagueId;
    private String playerName;
    private Integer heroId;
    private Integer equipId;
    private String equipName;
    private String equipIcon;
    private String equipDescGain;
    private String equipDescFunction;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
