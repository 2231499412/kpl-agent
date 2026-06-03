-- 给装备表加 league_id，支持按赛事筛选
ALTER TABLE battle_player_equip
    ADD COLUMN league_id VARCHAR(32) COMMENT '赛事ID' AFTER battle_id;

-- 回填已有数据：通过 battle -> match 关联
UPDATE battle_player_equip e
    INNER JOIN battle b ON e.battle_id COLLATE utf8mb4_unicode_ci = b.battle_id
    INNER JOIN `match` m ON b.match_id = m.match_id
SET e.league_id = m.league_id
WHERE e.league_id IS NULL;

CREATE INDEX idx_league_id ON battle_player_equip (league_id);
