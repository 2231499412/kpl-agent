package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据同步任务记录：保存每次同步的类型、范围、状态和错误信息，方便排查和重试。
 */
@Data
@TableName("sync_job")
public class SyncJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型，例如 LEAGUES、SEASON、LATEST_SEASON。 */
    private String jobType;

    /** 本次同步的数据范围，例如 leagueId、matchId 或 latest。 */
    private String target;

    /** 是否执行深度同步。 */
    private Boolean deepSync;

    /** RUNNING / SUCCESS / FAILED。 */
    private String status;

    /** 同步结果摘要。 */
    private String resultMessage;

    /** 失败时的错误信息。 */
    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
