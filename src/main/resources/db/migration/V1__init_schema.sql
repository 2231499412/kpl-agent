CREATE TABLE IF NOT EXISTS league (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id VARCHAR(32) NOT NULL COMMENT '赛事ID',
    league_name VARCHAR(128) NOT NULL COMMENT '赛事名称',
    league_type VARCHAR(32) COMMENT '赛事类型',
    year INT COMMENT '年份',
    season INT COMMENT '赛季',
    cc_league_id VARCHAR(32) COMMENT '内部赛事ID',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    status INT DEFAULT 0 COMMENT '状态',
    league_icon VARCHAR(512) COMMENT '赛事图标',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_league_id (league_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事表';

CREATE TABLE IF NOT EXISTS `match` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id VARCHAR(32) NOT NULL COMMENT '比赛ID',
    league_id VARCHAR(32) NOT NULL COMMENT '所属赛事ID',
    camp1_team_id VARCHAR(32) COMMENT '队伍1 ID',
    camp1_team_name VARCHAR(64) COMMENT '队伍1 名称',
    camp1_score INT DEFAULT 0 COMMENT '队伍1 得分',
    camp2_team_id VARCHAR(32) COMMENT '队伍2 ID',
    camp2_team_name VARCHAR(64) COMMENT '队伍2 名称',
    camp2_score INT DEFAULT 0 COMMENT '队伍2 得分',
    bo INT COMMENT 'BO数',
    win_camp INT COMMENT '获胜方',
    status INT DEFAULT 0 COMMENT '状态',
    match_stage VARCHAR(32) COMMENT '赛段',
    match_stage_desc VARCHAR(64) COMMENT '赛段描述',
    start_time DATETIME COMMENT '开赛时间',
    match_address VARCHAR(128) COMMENT '比赛地点',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_match_id (match_id),
    KEY idx_league_id (league_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛表';

CREATE TABLE IF NOT EXISTS battle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    battle_id VARCHAR(64) NOT NULL COMMENT '对局ID',
    match_id VARCHAR(32) NOT NULL COMMENT '所属比赛ID',
    battle_seq INT COMMENT '第几局',
    win_camp INT COMMENT '获胜方',
    game_duration BIGINT COMMENT '对局时长',
    status INT DEFAULT 0 COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_battle_id (battle_id),
    KEY idx_match_id (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局表';

CREATE TABLE IF NOT EXISTS battle_player (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    battle_id VARCHAR(64) NOT NULL COMMENT '对局ID',
    team_id VARCHAR(32) COMMENT '战队ID',
    team_name VARCHAR(64) COMMENT '战队名称',
    player_name VARCHAR(64) COMMENT '选手名',
    hero_id INT COMMENT '英雄ID',
    hero_name VARCHAR(32) COMMENT '英雄名',
    camp INT COMMENT '阵营',
    kill_num INT DEFAULT 0 COMMENT '击杀',
    death_num INT DEFAULT 0 COMMENT '死亡',
    assist_num INT DEFAULT 0 COMMENT '助攻',
    gold INT DEFAULT 0 COMMENT '经济',
    hurt_total BIGINT DEFAULT 0 COMMENT '总伤害',
    hurt_to_hero BIGINT DEFAULT 0 COMMENT '对英雄伤害',
    be_hurt_total BIGINT DEFAULT 0 COMMENT '总承伤',
    kda DOUBLE DEFAULT 0 COMMENT 'KDA',
    mvp_score DOUBLE DEFAULT 0 COMMENT 'MVP评分',
    is_mvp TINYINT DEFAULT 0 COMMENT '是否MVP',
    participation_rate DOUBLE DEFAULT 0 COMMENT '参团率',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_battle_id (battle_id),
    KEY idx_player_name (player_name),
    KEY idx_hero_id (hero_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局选手数据表';

CREATE TABLE IF NOT EXISTS hero_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id VARCHAR(32) NOT NULL COMMENT '赛事ID',
    hero_id INT NOT NULL COMMENT '英雄ID',
    hero_name VARCHAR(32) COMMENT '英雄名',
    hero_icon VARCHAR(512) COMMENT '英雄头像',
    battle_count INT DEFAULT 0 COMMENT '出场次数',
    win_rate DOUBLE DEFAULT 0 COMMENT '胜率',
    avg_kill DOUBLE DEFAULT 0 COMMENT '场均击杀',
    avg_death DOUBLE DEFAULT 0 COMMENT '场均死亡',
    avg_assist DOUBLE DEFAULT 0 COMMENT '场均助攻',
    avg_kda DOUBLE DEFAULT 0 COMMENT '场均KDA',
    avg_gold DOUBLE DEFAULT 0 COMMENT '场均经济',
    ban_num INT DEFAULT 0 COMMENT '被ban次数',
    ban_rate DOUBLE DEFAULT 0 COMMENT '被ban率',
    pick_num INT DEFAULT 0 COMMENT '被pick次数',
    pick_rate DOUBLE DEFAULT 0 COMMENT '被pick率',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_league_hero (league_id, hero_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='英雄赛季统计表';

CREATE TABLE IF NOT EXISTS player_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id VARCHAR(32) NOT NULL COMMENT '赛事ID',
    player_name VARCHAR(64) NOT NULL COMMENT '选手名',
    team_name VARCHAR(64) COMMENT '战队名',
    position VARCHAR(16) COMMENT '位置',
    position_desc VARCHAR(16) COMMENT '位置描述',
    battle_count INT DEFAULT 0 COMMENT '出场次数',
    win_rate DOUBLE DEFAULT 0 COMMENT '胜率',
    avg_kill DOUBLE DEFAULT 0 COMMENT '场均击杀',
    avg_death DOUBLE DEFAULT 0 COMMENT '场均死亡',
    avg_assist DOUBLE DEFAULT 0 COMMENT '场均助攻',
    avg_kda DOUBLE DEFAULT 0 COMMENT '场均KDA',
    avg_gold DOUBLE DEFAULT 0 COMMENT '场均经济',
    avg_hurt_to_hero DOUBLE DEFAULT 0 COMMENT '场均对英雄伤害',
    avg_be_hurt DOUBLE DEFAULT 0 COMMENT '场均承伤',
    avg_participation_rate DOUBLE DEFAULT 0 COMMENT '场均参团率',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_league_player (league_id, player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选手赛季统计表';

CREATE TABLE IF NOT EXISTS team_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id VARCHAR(32) NOT NULL COMMENT '赛事ID',
    team_id VARCHAR(32) NOT NULL COMMENT '战队ID',
    team_name VARCHAR(64) COMMENT '战队名',
    team_icon VARCHAR(512) COMMENT '战队图标',
    battle_count INT DEFAULT 0 COMMENT '出场次数',
    win_rate DOUBLE DEFAULT 0 COMMENT '胜率',
    avg_kill DOUBLE DEFAULT 0 COMMENT '场均击杀',
    avg_death DOUBLE DEFAULT 0 COMMENT '场均死亡',
    avg_kda DOUBLE DEFAULT 0 COMMENT '场均KDA',
    avg_gold DOUBLE DEFAULT 0 COMMENT '场均经济',
    avg_first_blood DOUBLE DEFAULT 0 COMMENT '场均一血率',
    avg_push_tower DOUBLE DEFAULT 0 COMMENT '场均推塔',
    avg_dragon_control_rate DOUBLE DEFAULT 0 COMMENT '场均龙控制率',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_league_team (league_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='战队赛季统计表';

CREATE TABLE IF NOT EXISTS sync_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL COMMENT '任务类型',
    target VARCHAR(64) COMMENT '同步范围',
    deep_sync TINYINT(1) DEFAULT 0 COMMENT '是否深度同步',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    result_message VARCHAR(512) COMMENT '结果摘要',
    error_message TEXT COMMENT '错误信息',
    started_at DATETIME COMMENT '开始时间',
    finished_at DATETIME COMMENT '结束时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_job_status (job_type, status),
    KEY idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据同步任务表';

CREATE TABLE IF NOT EXISTS sync_cursor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cursor_key VARCHAR(128) NOT NULL COMMENT '游标唯一键',
    cursor_value VARCHAR(256) COMMENT '游标值',
    last_success_at DATETIME COMMENT '最近成功时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cursor_key (cursor_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据同步游标表';
