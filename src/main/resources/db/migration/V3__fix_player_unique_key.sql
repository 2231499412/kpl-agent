-- 选手同名不同队的情况（如2022年有两个"kk"），需要把 team_name 加入唯一键
ALTER TABLE player_stats DROP INDEX uk_league_player, ADD UNIQUE KEY uk_league_player (league_id, player_name, team_name);
