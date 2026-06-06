package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 铭文基础信息 */
@Data
@TableName("inscription_info")
public class InscriptionInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mingId;
    private String mingName;
    private String mingType;
    private Integer mingGrade;
    private String mingDes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
