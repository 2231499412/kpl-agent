package com.kpl.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 装备基础信息（价格、属性等静态数据） */
@Data
@TableName("equip_info")
public class EquipBaseInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer itemId;
    private String itemName;
    private Integer itemType;
    private Integer price;
    private Integer totalPrice;
    private String des1;
    private String des2;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
