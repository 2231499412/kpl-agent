ALTER TABLE `match` ADD INDEX idx_league_status_time (league_id, status, start_time);
