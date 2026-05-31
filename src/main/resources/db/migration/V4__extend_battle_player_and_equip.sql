-- 扩展 battle_player 表：新增装备、召唤师技能、伤害占比、位置等字段
ALTER TABLE battle_player
    ADD COLUMN summoner_ability_id INT COMMENT '召唤师技能ID' AFTER participation_rate,
    ADD COLUMN summoner_ability_name VARCHAR(32) COMMENT '召唤师技能名称' AFTER summoner_ability_id,
    ADD COLUMN hurt_total_rate DOUBLE DEFAULT 0 COMMENT '伤害占比' AFTER summoner_ability_name,
    ADD COLUMN be_hurt_total_rate DOUBLE DEFAULT 0 COMMENT '承伤占比' AFTER hurt_total_rate,
    ADD COLUMN hurt_to_hero_total BIGINT DEFAULT 0 COMMENT '对英雄总伤害' AFTER be_hurt_total_rate,
    ADD COLUMN be_hurt_by_hero_total BIGINT DEFAULT 0 COMMENT '被英雄总伤害' AFTER hurt_to_hero_total,
    ADD COLUMN hurt_to_hero_total_rate DOUBLE DEFAULT 0 COMMENT '对英雄伤害占比' AFTER hurt_to_hero_total,
    ADD COLUMN be_hurt_by_hero_total_rate DOUBLE DEFAULT 0 COMMENT '被英雄伤害占比' AFTER hurt_to_hero_total_rate,
    ADD COLUMN position INT COMMENT '位置编号' AFTER be_hurt_by_hero_total_rate,
    ADD COLUMN position_desc VARCHAR(16) COMMENT '位置描述' AFTER position,
    ADD COLUMN is_lose_mvp TINYINT DEFAULT 0 COMMENT '是否败方MVP' AFTER position_desc,
    ADD COLUMN symbol_ids VARCHAR(128) COMMENT '铭文ID列表' AFTER is_lose_mvp;

-- 新建对局选手装备表：每局每个选手最多6件装备
CREATE TABLE IF NOT EXISTS battle_player_equip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    battle_id VARCHAR(64) NOT NULL COMMENT '对局ID',
    player_name VARCHAR(64) NOT NULL COMMENT '选手名',
    hero_id INT NOT NULL COMMENT '英雄ID',
    equip_id INT NOT NULL COMMENT '装备ID',
    equip_name VARCHAR(64) COMMENT '装备名称',
    equip_icon VARCHAR(512) COMMENT '装备图标',
    equip_desc_gain TEXT COMMENT '装备属性描述',
    equip_desc_function TEXT COMMENT '装备功能描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_battle_player (battle_id, player_name),
    KEY idx_equip_id (equip_id),
    KEY idx_hero_id (hero_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局选手装备表';
