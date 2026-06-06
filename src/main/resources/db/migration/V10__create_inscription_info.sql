CREATE TABLE IF NOT EXISTS inscription_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ming_id VARCHAR(16) NOT NULL COMMENT '铭文ID',
    ming_name VARCHAR(64) COMMENT '铭文名称',
    ming_type VARCHAR(16) COMMENT '铭文颜色: red/blue/yellow',
    ming_grade INT COMMENT '铭文等级(1-5)',
    ming_des TEXT COMMENT '铭文属性描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ming_id (ming_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='铭文基础信息表';
