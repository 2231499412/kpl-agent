-- 装备购买顺序
ALTER TABLE battle_player_equip ADD COLUMN equip_order INT DEFAULT 0 COMMENT '装备购买顺序(0=未知)';
CREATE INDEX idx_equip_order ON battle_player_equip(equip_order);
