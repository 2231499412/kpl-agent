package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步游标：记录某类增量任务最近一次成功执行的位置和时间。
 */
@Data
@TableName("sync_cursor")
public class SyncCursor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游标唯一键，例如 latest_season、season:20260001。 */
    private String cursorKey;

    /** 游标值，MVP 阶段可存 leagueId / matchId / 时间戳等。 */
    private String cursorValue;

    private LocalDateTime lastSuccessAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
