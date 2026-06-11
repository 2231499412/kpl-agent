package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 对局实体：一场比赛中的每个小局 */
@Data
@TableName("battle")
public class Battle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String battleId;
    private String matchId;

    /** 第几局 */
    private Integer battleSeq;

    /** B站视频BV号 */
    private String bvid;

    /** 获胜方：1/2 */
    private Integer winCamp;

    /** 对局时长(毫秒) */
    private Long gameDuration;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
