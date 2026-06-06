CREATE TABLE IF NOT EXISTS equip_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id INT NOT NULL COMMENT '装备ID (对应 pvp.qq.com item.json)',
    item_name VARCHAR(64) COMMENT '装备名称',
    item_type INT COMMENT '装备类型',
    price INT DEFAULT 0 COMMENT '组件价格',
    total_price INT DEFAULT 0 COMMENT '总价',
    des1 TEXT COMMENT '属性描述',
    des2 TEXT COMMENT '被动效果描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_item_id (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装备基础信息表';
