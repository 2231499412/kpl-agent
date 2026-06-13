package com.kpl.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kpl.agent.entity.BattlePlayer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BattlePlayerMapper extends BaseMapper<BattlePlayer> {

    /** 从对局明细即时聚合选手总体数据，用于 player_stats 汇总表缺失时兜底。 */
    @Select("""
            SELECT
                SUBSTRING_INDEX(bp.player_name, '.', -1) AS playerName,
                MAX(bp.team_name) AS teamName,
                MAX(bp.position) AS position,
                MAX(bp.position_desc) AS positionDesc,
                COUNT(*) AS battleCount,
                ROUND(SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) * 1.0 / COUNT(*), 4) AS winRate,
                ROUND(AVG(bp.kill_num), 2) AS avgKill,
                ROUND(AVG(bp.death_num), 2) AS avgDeath,
                ROUND(AVG(bp.assist_num), 2) AS avgAssist,
                ROUND(AVG(bp.kda), 2) AS avgKda,
                ROUND(AVG(bp.gold), 2) AS avgGold,
                ROUND(AVG(bp.hurt_to_hero), 2) AS avgHurtToHero,
                ROUND(AVG(bp.be_hurt_total), 2) AS avgBeHurt,
                ROUND(AVG(bp.participation_rate), 2) AS avgParticipationRate
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY SUBSTRING_INDEX(bp.player_name, '.', -1)
            ORDER BY battleCount DESC
            LIMIT 5
            """)
    List<Map<String, Object>> aggregatePlayerStats(@Param("playerName") String playerName,
                                                    @Param("leagueId") String leagueId);

    /** 查询选手的英雄胜率数据 */
    @Select("""
            SELECT bp.hero_id AS heroId, MAX(bp.hero_name) AS heroName,
                   COUNT(*) AS games,
                   SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate,
                   ROUND(AVG(bp.kda), 2) AS avgKda
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE bp.player_name LIKE CONCAT('%', #{playerName}, '%')
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY bp.hero_id
            HAVING games >= 2
            ORDER BY games DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> playerHeroStats(@Param("playerName") String playerName,
                                               @Param("leagueId") String leagueId,
                                               @Param("limit") int limit);

    @Select("""
            SELECT bp.battle_id AS battleId,
                   b.match_id AS matchId,
                   b.battle_seq AS battleSeq,
                   b.game_duration AS gameDuration,
                   m.match_stage AS matchStage,
                   m.match_stage_desc AS matchStageDesc,
                   m.start_time AS startTime,
                   m.camp1_team_name AS camp1TeamName,
                   m.camp1_score AS camp1Score,
                   m.camp2_team_name AS camp2TeamName,
                   m.camp2_score AS camp2Score,
                   bp.team_name AS teamName,
                   bp.player_name AS playerName,
                   bp.position_desc AS positionDesc,
                   bp.hero_id AS heroId,
                   bp.hero_name AS heroName,
                   CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END AS won,
                   bp.kill_num AS killNum,
                   bp.death_num AS deathNum,
                   bp.assist_num AS assistNum,
                   bp.gold AS gold,
                   bp.hurt_to_hero AS hurtToHero,
                   bp.be_hurt_total AS beHurtTotal,
                   bp.kda AS kda,
                   bp.mvp_score AS mvpScore,
                   bp.is_mvp AS isMvp,
                   bp.is_lose_mvp AS isLoseMvp,
                   bp.participation_rate AS participationRate,
                   ROUND(CASE
                       WHEN b.game_duration > 10000 THEN bp.gold * 60000.0 / b.game_duration
                       WHEN b.game_duration > 0 THEN bp.gold * 60.0 / b.game_duration
                       ELSE NULL END, 1) AS goldPerMinute,
                   ROUND(CASE
                       WHEN b.game_duration > 10000 THEN bp.hurt_to_hero * 60000.0 / b.game_duration
                       WHEN b.game_duration > 0 THEN bp.hurt_to_hero * 60.0 / b.game_duration
                       ELSE NULL END, 1) AS hurtToHeroPerMinute,
                   ROUND(CASE
                       WHEN b.game_duration > 10000 THEN bp.be_hurt_total * 60000.0 / b.game_duration
                       WHEN b.game_duration > 0 THEN bp.be_hurt_total * 60.0 / b.game_duration
                       ELSE NULL END, 1) AS beHurtPerMinute
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            ORDER BY m.start_time DESC, b.battle_seq DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> playerRecentGames(@Param("playerName") String playerName,
                                                 @Param("leagueId") String leagueId,
                                                 @Param("limit") int limit);

    @Select("""
            SELECT b.match_id AS matchId,
                   m.match_stage AS matchStage,
                   m.match_stage_desc AS matchStageDesc,
                   m.start_time AS startTime,
                   m.camp1_team_name AS camp1TeamName,
                   m.camp1_score AS camp1Score,
                   m.camp2_team_name AS camp2TeamName,
                   m.camp2_score AS camp2Score,
                   bp.team_name AS teamName,
                   bp.player_name AS playerName,
                   COUNT(*) AS battleCount,
                   SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   CASE
                       WHEN (bp.team_name = m.camp1_team_name AND m.camp1_score > m.camp2_score)
                         OR (bp.team_name = m.camp2_team_name AND m.camp2_score > m.camp1_score)
                       THEN 1 ELSE 0 END AS won,
                   ROUND(AVG(bp.kill_num), 2) AS killNum,
                   ROUND(AVG(bp.death_num), 2) AS deathNum,
                   ROUND(AVG(bp.assist_num), 2) AS assistNum,
                   ROUND(AVG(bp.kda), 2) AS kda,
                   ROUND(AVG(bp.gold), 2) AS gold,
                   ROUND(AVG(bp.hurt_to_hero), 2) AS hurtToHero,
                   ROUND(AVG(bp.be_hurt_total), 2) AS beHurtTotal,
                   ROUND(AVG(bp.participation_rate), 2) AS participationRate,
                   ROUND(AVG(CASE
                       WHEN b.game_duration > 10000 THEN bp.gold * 60000.0 / b.game_duration
                       WHEN b.game_duration > 0 THEN bp.gold * 60.0 / b.game_duration
                       ELSE NULL END), 1) AS goldPerMinute,
                   ROUND(AVG(CASE
                       WHEN b.game_duration > 10000 THEN bp.hurt_to_hero * 60000.0 / b.game_duration
                       WHEN b.game_duration > 0 THEN bp.hurt_to_hero * 60.0 / b.game_duration
                       ELSE NULL END), 1) AS hurtToHeroPerMinute,
                   ROUND(AVG(CASE
                       WHEN b.game_duration > 10000 THEN bp.be_hurt_total * 60000.0 / b.game_duration
                       WHEN b.game_duration > 0 THEN bp.be_hurt_total * 60.0 / b.game_duration
                       ELSE NULL END), 1) AS beHurtPerMinute,
                   MAX(bp.hero_name) AS heroName
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY b.match_id, bp.team_name, bp.player_name
            ORDER BY m.start_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> playerRecentMatches(@Param("playerName") String playerName,
                                                   @Param("leagueId") String leagueId,
                                                   @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS matches,
                   SUM(battleCount) AS games,
                   SUM(battleWins) AS battleWins,
                   ROUND(SUM(battleWins) * 100.0 / SUM(battleCount), 1) AS battleWinRate,
                   SUM(won) AS wins,
                   ROUND(SUM(won) * 100.0 / COUNT(*), 1) AS winRate,
                   ROUND(AVG(killNum), 2) AS killNum,
                   ROUND(AVG(deathNum), 2) AS deathNum,
                   ROUND(AVG(assistNum), 2) AS assistNum,
                   ROUND(AVG(kda), 2) AS kda,
                   ROUND(AVG(gold), 2) AS gold,
                   ROUND(AVG(hurtToHero), 2) AS hurtToHero,
                   ROUND(AVG(beHurtTotal), 2) AS beHurtTotal,
                   ROUND(AVG(participationRate), 2) AS participationRate,
                   ROUND(AVG(goldPerMinute), 1) AS goldPerMinute,
                   ROUND(AVG(hurtToHeroPerMinute), 1) AS hurtToHeroPerMinute,
                   ROUND(AVG(beHurtPerMinute), 1) AS beHurtPerMinute
            FROM (
                SELECT b.match_id AS matchId,
                       COUNT(*) AS battleCount,
                       SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS battleWins,
                       CASE
                           WHEN (bp.team_name = m.camp1_team_name AND m.camp1_score > m.camp2_score)
                             OR (bp.team_name = m.camp2_team_name AND m.camp2_score > m.camp1_score)
                           THEN 1 ELSE 0 END AS won,
                       ROUND(AVG(bp.kill_num), 2) AS killNum,
                       ROUND(AVG(bp.death_num), 2) AS deathNum,
                       ROUND(AVG(bp.assist_num), 2) AS assistNum,
                       ROUND(AVG(bp.kda), 2) AS kda,
                       ROUND(AVG(bp.gold), 2) AS gold,
                       ROUND(AVG(bp.hurt_to_hero), 2) AS hurtToHero,
                       ROUND(AVG(bp.be_hurt_total), 2) AS beHurtTotal,
                       ROUND(AVG(bp.participation_rate), 2) AS participationRate,
                       ROUND(AVG(CASE
                           WHEN b.game_duration > 10000 THEN bp.gold * 60000.0 / b.game_duration
                           WHEN b.game_duration > 0 THEN bp.gold * 60.0 / b.game_duration
                           ELSE NULL END), 1) AS goldPerMinute,
                       ROUND(AVG(CASE
                           WHEN b.game_duration > 10000 THEN bp.hurt_to_hero * 60000.0 / b.game_duration
                           WHEN b.game_duration > 0 THEN bp.hurt_to_hero * 60.0 / b.game_duration
                           ELSE NULL END), 1) AS hurtToHeroPerMinute,
                       ROUND(AVG(CASE
                           WHEN b.game_duration > 10000 THEN bp.be_hurt_total * 60000.0 / b.game_duration
                           WHEN b.game_duration > 0 THEN bp.be_hurt_total * 60.0 / b.game_duration
                           ELSE NULL END), 1) AS beHurtPerMinute
                FROM battle_player bp
                JOIN battle b ON bp.battle_id = b.battle_id
                JOIN `match` m ON b.match_id = m.match_id
                WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                    OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
                  AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
                GROUP BY b.match_id, bp.team_name, bp.player_name
            ) match_rows
            """)
    Map<String, Object> playerSeasonMatchAverages(@Param("playerName") String playerName,
                                                  @Param("leagueId") String leagueId);

    @Select("""
            SELECT m.match_stage AS stage,
                   MAX(m.match_stage_desc) AS stageDesc,
                   COUNT(*) AS games,
                   SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate,
                   ROUND(AVG(bp.kda), 2) AS avgKda,
                   ROUND(AVG(bp.kill_num), 2) AS avgKill,
                   ROUND(AVG(bp.death_num), 2) AS avgDeath,
                   ROUND(AVG(bp.assist_num), 2) AS avgAssist,
                   SUBSTRING_INDEX(GROUP_CONCAT(bp.hero_name ORDER BY bp.hero_name SEPARATOR ','), ',', 1) AS mainHero
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY m.match_stage
            ORDER BY MIN(m.start_time)
            """)
    List<Map<String, Object>> playerStageStats(@Param("playerName") String playerName,
                                                @Param("leagueId") String leagueId);

    @Select("""
            SELECT m.league_id AS leagueId,
                   MAX(l.league_name) AS leagueName,
                   MAX(l.year) AS year,
                   MAX(l.season) AS season,
                   MIN(COALESCE(l.start_time, m.start_time)) AS startTime,
                   COUNT(*) AS games,
                   COUNT(DISTINCT m.match_id) AS matches,
                   COUNT(DISTINCT CASE
                       WHEN (bp.team_name = m.camp1_team_name AND m.camp1_score > m.camp2_score)
                         OR (bp.team_name = m.camp2_team_name AND m.camp2_score > m.camp1_score)
                       THEN m.match_id END) AS wins,
                   ROUND(COUNT(DISTINCT CASE
                       WHEN (bp.team_name = m.camp1_team_name AND m.camp1_score > m.camp2_score)
                         OR (bp.team_name = m.camp2_team_name AND m.camp2_score > m.camp1_score)
                       THEN m.match_id END) * 100.0 / COUNT(DISTINCT m.match_id), 1) AS winRate,
                   ROUND(AVG(bp.kda), 2) AS avgKda,
                   ROUND(AVG(bp.kill_num), 2) AS avgKill,
                   ROUND(AVG(bp.death_num), 2) AS avgDeath,
                   ROUND(AVG(bp.assist_num), 2) AS avgAssist,
                   ROUND(AVG(bp.gold), 2) AS avgGold,
                   ROUND(AVG(bp.participation_rate), 4) AS avgParticipationRate,
                   COUNT(DISTINCT bp.hero_id) AS heroCount
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            LEFT JOIN league l ON l.league_id = m.league_id
            WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
            GROUP BY m.league_id
            HAVING games >= 1
            ORDER BY startTime DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> playerLeagueTimeline(@Param("playerName") String playerName,
                                                    @Param("limit") int limit);

    @Select("""
            WITH target_position_rows AS (
                SELECT m.league_id AS league_id,
                       bp.position AS position,
                       bp.position_desc AS position_desc,
                       COUNT(*) AS games,
                       MIN(COALESCE(l.start_time, m.start_time)) AS start_time
                FROM battle_player bp
                JOIN battle b ON bp.battle_id = b.battle_id
                JOIN `match` m ON b.match_id = m.match_id
                LEFT JOIN league l ON l.league_id = m.league_id
                WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                    OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
                GROUP BY m.league_id, bp.position, bp.position_desc
            ),
            target_position_ranked AS (
                SELECT league_id,
                       position,
                       position_desc,
                       games,
                       start_time,
                       ROW_NUMBER() OVER (PARTITION BY league_id ORDER BY games DESC, position_desc) AS rn
                FROM target_position_rows
            ),
            target_leagues AS (
                SELECT league_id, position, position_desc, start_time
                FROM target_position_ranked
                WHERE rn = 1
                ORDER BY start_time DESC
                LIMIT #{limit}
            )
            SELECT m.league_id AS leagueId,
                   MAX(l.league_name) AS leagueName,
                   SUBSTRING_INDEX(bp.player_name, '.', -1) AS playerName,
                   bp.position AS position,
                   bp.position_desc AS positionDesc,
                   COUNT(*) AS games,
                   COUNT(DISTINCT m.match_id) AS matches,
                   COUNT(DISTINCT CASE
                       WHEN (bp.team_name = m.camp1_team_name AND m.camp1_score > m.camp2_score)
                         OR (bp.team_name = m.camp2_team_name AND m.camp2_score > m.camp1_score)
                       THEN m.match_id END) AS wins,
                   ROUND(COUNT(DISTINCT CASE
                       WHEN (bp.team_name = m.camp1_team_name AND m.camp1_score > m.camp2_score)
                         OR (bp.team_name = m.camp2_team_name AND m.camp2_score > m.camp1_score)
                       THEN m.match_id END) * 100.0 / COUNT(DISTINCT m.match_id), 1) AS winRate,
                   ROUND(AVG(bp.kda), 2) AS avgKda,
                    ROUND(AVG(CASE
                        WHEN b.game_duration > 10000 THEN bp.gold * 60000.0 / b.game_duration
                        WHEN b.game_duration > 0 THEN bp.gold * 60.0 / b.game_duration
                        ELSE NULL END), 2) AS avgGoldPerMinute,
                    ROUND(AVG(bp.participation_rate), 4) AS avgParticipationRate,
                    ROUND(AVG(CASE
                        WHEN b.game_duration > 10000 THEN bp.hurt_to_hero * 60000.0 / b.game_duration
                        WHEN b.game_duration > 0 THEN bp.hurt_to_hero * 60.0 / b.game_duration
                        ELSE NULL END), 2) AS avgHurtToHeroPerMinute,
                    ROUND(AVG(CASE
                        WHEN b.game_duration > 10000 THEN bp.be_hurt_total * 60000.0 / b.game_duration
                        WHEN b.game_duration > 0 THEN bp.be_hurt_total * 60.0 / b.game_duration
                        ELSE NULL END), 2) AS avgBeHurtPerMinute,
                   COUNT(DISTINCT bp.hero_id) AS heroCount,
                   MAX(CASE WHEN (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                       OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName}) THEN 1 ELSE 0 END) AS targetPlayer
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            LEFT JOIN league l ON l.league_id = m.league_id
            JOIN target_leagues tl ON tl.league_id = m.league_id
                AND (
                    (tl.position IS NOT NULL AND bp.position = tl.position)
                    OR (tl.position IS NULL AND tl.position_desc = bp.position_desc)
                )
            GROUP BY m.league_id, SUBSTRING_INDEX(bp.player_name, '.', -1), bp.position, bp.position_desc
            HAVING games >= 1
            ORDER BY MAX(tl.start_time) DESC, games DESC
            """)
    List<Map<String, Object>> playerLeaguePerformancePool(@Param("playerName") String playerName,
                                                           @Param("limit") int limit);

    @Select("""
            WITH target_team_rows AS (
                SELECT m.league_id AS league_id,
                       bp.team_name AS team_name,
                       COUNT(*) AS games,
                       MIN(COALESCE(l.start_time, m.start_time)) AS start_time
                FROM battle_player bp
                JOIN battle b ON bp.battle_id = b.battle_id
                JOIN `match` m ON b.match_id = m.match_id
                LEFT JOIN league l ON l.league_id = m.league_id
                WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                    OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
                  AND bp.team_name IS NOT NULL
                  AND bp.team_name <> ''
                GROUP BY m.league_id, bp.team_name
            ),
            target_team_ranked AS (
                SELECT league_id,
                       team_name,
                       games,
                       start_time,
                       ROW_NUMBER() OVER (PARTITION BY league_id ORDER BY games DESC, team_name) AS rn
                FROM target_team_rows
            ),
            target_leagues AS (
                SELECT league_id, start_time
                FROM target_team_ranked
                WHERE rn = 1
                ORDER BY start_time DESC
                LIMIT #{limit}
            ),
            match_battles AS (
                SELECT match_id,
                       COUNT(*) AS battle_count,
                       SUM(CASE WHEN win_camp = 1 THEN 1 ELSE 0 END) AS camp1_wins,
                       SUM(CASE WHEN win_camp = 2 THEN 1 ELSE 0 END) AS camp2_wins
                FROM battle
                GROUP BY match_id
            ),
            team_match_rows AS (
                SELECT m.league_id AS leagueId,
                       MAX(l.league_name) AS leagueName,
                       m.match_id AS matchId,
                       m.start_time AS startTime,
                       m.match_stage_desc AS stageDesc,
                       m.camp1_team_name AS teamName,
                       m.camp1_score AS teamScore,
                       m.camp2_score AS opponentScore,
                       CASE WHEN m.camp1_score > m.camp2_score THEN 1 ELSE 0 END AS won,
                       IFNULL(MAX(mb.battle_count), m.camp1_score + m.camp2_score) AS battleCount,
                       IFNULL(MAX(mb.camp1_wins), m.camp1_score) AS battleWins
                FROM `match` m
                JOIN target_leagues tl ON tl.league_id = m.league_id
                LEFT JOIN league l ON l.league_id = m.league_id
                LEFT JOIN match_battles mb ON mb.match_id = m.match_id
                WHERE m.camp1_team_name IS NOT NULL AND m.camp1_team_name <> ''
                GROUP BY m.league_id, m.match_id, m.start_time, m.match_stage_desc,
                         m.camp1_team_name, m.camp1_score, m.camp2_score
                UNION ALL
                SELECT m.league_id AS leagueId,
                       MAX(l.league_name) AS leagueName,
                       m.match_id AS matchId,
                       m.start_time AS startTime,
                       m.match_stage_desc AS stageDesc,
                       m.camp2_team_name AS teamName,
                       m.camp2_score AS teamScore,
                       m.camp1_score AS opponentScore,
                       CASE WHEN m.camp2_score > m.camp1_score THEN 1 ELSE 0 END AS won,
                       IFNULL(MAX(mb.battle_count), m.camp1_score + m.camp2_score) AS battleCount,
                       IFNULL(MAX(mb.camp2_wins), m.camp2_score) AS battleWins
                FROM `match` m
                JOIN target_leagues tl ON tl.league_id = m.league_id
                LEFT JOIN league l ON l.league_id = m.league_id
                LEFT JOIN match_battles mb ON mb.match_id = m.match_id
                WHERE m.camp2_team_name IS NOT NULL AND m.camp2_team_name <> ''
                GROUP BY m.league_id, m.match_id, m.start_time, m.match_stage_desc,
                         m.camp2_team_name, m.camp2_score, m.camp1_score
            ),
            team_ranked AS (
                SELECT team_match_rows.*,
                       ROW_NUMBER() OVER (PARTITION BY leagueId, teamName ORDER BY startTime DESC, matchId DESC) AS rn
                FROM team_match_rows
            )
            SELECT tr.leagueId AS leagueId,
                   MAX(tr.leagueName) AS leagueName,
                   tr.teamName AS teamName,
                   COUNT(*) AS matches,
                   SUM(tr.won) AS matchWins,
                   ROUND(SUM(tr.won) * 100.0 / COUNT(*), 1) AS matchWinRate,
                   SUM(tr.battleCount) AS games,
                   SUM(tr.battleWins) AS battleWins,
                   ROUND(SUM(tr.battleWins) * 100.0 / NULLIF(SUM(tr.battleCount), 0), 1) AS battleWinRate,
                   SUM(tr.battleWins) - SUM(tr.battleCount - tr.battleWins) AS gameDiff,
                   ROUND((SUM(tr.battleWins) - SUM(tr.battleCount - tr.battleWins)) * 1.0 / COUNT(*), 2) AS gameDiffPerMatch,
                   MAX(CASE WHEN tr.rn = 1 THEN tr.stageDesc END) AS lastStageDesc,
                   MAX(CASE WHEN tr.rn = 1 THEN tr.won END) AS lastWon,
                   MAX(CASE WHEN ttr.rn = 1 AND ttr.team_name = tr.teamName THEN 1 ELSE 0 END) AS targetTeam
            FROM team_ranked tr
            LEFT JOIN target_team_ranked ttr ON ttr.league_id = tr.leagueId
            GROUP BY tr.leagueId, tr.teamName
            ORDER BY MAX(tr.startTime) DESC, matches DESC
            """)
    List<Map<String, Object>> playerLeagueTeamResults(@Param("playerName") String playerName,
                                                       @Param("limit") int limit);

    @Select("""
            WITH match_battles AS (
                SELECT match_id,
                       COUNT(*) AS battle_count,
                       SUM(CASE WHEN win_camp = 1 THEN 1 ELSE 0 END) AS camp1_wins,
                       SUM(CASE WHEN win_camp = 2 THEN 1 ELSE 0 END) AS camp2_wins
                FROM battle
                GROUP BY match_id
            ),
            team_match_rows AS (
                SELECT m.league_id AS leagueId,
                       m.match_id AS matchId,
                       m.start_time AS startTime,
                       m.match_stage_desc AS stageDesc,
                       m.camp1_team_name AS teamName,
                       CASE WHEN m.camp1_score > m.camp2_score THEN 1 ELSE 0 END AS won,
                       IFNULL(MAX(mb.battle_count), m.camp1_score + m.camp2_score) AS battleCount,
                       IFNULL(MAX(mb.camp1_wins), m.camp1_score) AS battleWins
                FROM `match` m
                LEFT JOIN match_battles mb ON mb.match_id = m.match_id
                WHERE m.league_id = #{leagueId}
                  AND m.camp1_team_name IS NOT NULL AND m.camp1_team_name <> ''
                GROUP BY m.league_id, m.match_id, m.start_time, m.match_stage_desc,
                         m.camp1_team_name, m.camp1_score, m.camp2_score
                UNION ALL
                SELECT m.league_id AS leagueId,
                       m.match_id AS matchId,
                       m.start_time AS startTime,
                       m.match_stage_desc AS stageDesc,
                       m.camp2_team_name AS teamName,
                       CASE WHEN m.camp2_score > m.camp1_score THEN 1 ELSE 0 END AS won,
                       IFNULL(MAX(mb.battle_count), m.camp1_score + m.camp2_score) AS battleCount,
                       IFNULL(MAX(mb.camp2_wins), m.camp2_score) AS battleWins
                FROM `match` m
                LEFT JOIN match_battles mb ON mb.match_id = m.match_id
                WHERE m.league_id = #{leagueId}
                  AND m.camp2_team_name IS NOT NULL AND m.camp2_team_name <> ''
                GROUP BY m.league_id, m.match_id, m.start_time, m.match_stage_desc,
                         m.camp2_team_name, m.camp2_score, m.camp1_score
            ),
            team_ranked AS (
                SELECT team_match_rows.*,
                       ROW_NUMBER() OVER (PARTITION BY teamName ORDER BY startTime DESC, matchId DESC) AS rn
                FROM team_match_rows
            )
            SELECT leagueId,
                   teamName,
                   COUNT(*) AS matches,
                   SUM(won) AS matchWins,
                   ROUND(SUM(won) * 100.0 / COUNT(*), 1) AS matchWinRate,
                   SUM(battleCount) AS games,
                   SUM(battleWins) AS battleWins,
                   ROUND(SUM(battleWins) * 100.0 / NULLIF(SUM(battleCount), 0), 1) AS battleWinRate,
                   SUM(battleWins) - SUM(battleCount - battleWins) AS gameDiff,
                   ROUND((SUM(battleWins) - SUM(battleCount - battleWins)) * 1.0 / COUNT(*), 2) AS gameDiffPerMatch,
                   MAX(CASE WHEN rn = 1 THEN stageDesc END) AS lastStageDesc,
                   MAX(CASE WHEN rn = 1 THEN won END) AS lastWon,
                   MAX(startTime) AS lastMatchTime,
                   MIN(startTime) AS firstMatchTime
            FROM team_ranked
            GROUP BY leagueId, teamName
            ORDER BY matches DESC, matchWinRate DESC
            """)
    List<Map<String, Object>> leagueTeamResults(@Param("leagueId") String leagueId);

    @Select("""
            SELECT MIN(start_time) AS firstTime, MAX(start_time) AS lastTime,
                   COUNT(*) AS totalMatches
            FROM `match`
            WHERE league_id = #{leagueId} AND start_time IS NOT NULL
            """)
    Map<String, Object> leagueTimespan(@Param("leagueId") String leagueId);

    @Select("""
            WITH player_matches AS (
                SELECT SUBSTRING_INDEX(bp.player_name, '.', -1) AS playerName,
                       MAX(bp.team_name) AS teamName,
                       MAX(bp.position_desc) AS positionDesc,
                       m.match_id AS matchId,
                       MAX(m.start_time) AS startTime,
                       CASE
                           WHEN (bp.team_name = m.camp1_team_name AND m.camp1_score > m.camp2_score)
                             OR (bp.team_name = m.camp2_team_name AND m.camp2_score > m.camp1_score)
                           THEN 1 ELSE 0 END AS won,
                       COUNT(*) AS battleCount,
                       SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS battleWins
                FROM battle_player bp
                JOIN battle b ON bp.battle_id = b.battle_id
                JOIN `match` m ON b.match_id = m.match_id
                WHERE m.league_id = #{leagueId}
                  AND bp.player_name IS NOT NULL
                  AND bp.player_name <> ''
                GROUP BY SUBSTRING_INDEX(bp.player_name, '.', -1), m.match_id, bp.team_name,
                         m.camp1_team_name, m.camp2_team_name, m.camp1_score, m.camp2_score
            ),
            ranked AS (
                SELECT player_matches.*,
                       ROW_NUMBER() OVER (PARTITION BY playerName ORDER BY startTime DESC, matchId DESC) AS rn
                FROM player_matches
            )
            SELECT playerName,
                   COUNT(*) AS recent5Matches,
                   SUM(won) AS recent5MatchWins,
                   ROUND(SUM(won) * 100.0 / COUNT(*), 1) AS recent5WinRate,
                   SUM(battleCount) AS recent5Games,
                   SUM(battleWins) AS recent5BattleWins,
                   ROUND(SUM(battleWins) * 100.0 / NULLIF(SUM(battleCount), 0), 1) AS recent5BattleWinRate
            FROM ranked
            WHERE rn <= 5
            GROUP BY playerName
            """)
    List<Map<String, Object>> playerRecent5Summaries(@Param("leagueId") String leagueId);

    @Select("""
            SELECT m.league_id AS leagueId,
                   MAX(l.league_name) AS leagueName,
                   bp.hero_id AS heroId,
                   MAX(bp.hero_name) AS heroName,
                   COUNT(*) AS games,
                   SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate,
                   ROUND(AVG(bp.kda), 2) AS avgKda,
                   ROUND(AVG(bp.kill_num), 2) AS avgKill,
                   ROUND(AVG(bp.death_num), 2) AS avgDeath,
                   ROUND(AVG(bp.assist_num), 2) AS avgAssist,
                   MIN(COALESCE(l.start_time, m.start_time)) AS startTime
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            LEFT JOIN league l ON l.league_id = m.league_id
            WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
            GROUP BY m.league_id, bp.hero_id
            HAVING games >= #{minGames}
            ORDER BY startTime DESC, games DESC, winRate DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> playerLeagueHeroMatrix(@Param("playerName") String playerName,
                                                      @Param("minGames") int minGames,
                                                      @Param("limit") int limit);

    @Select("""
            SELECT bp.battle_id AS battleId,
                   b.match_id AS matchId,
                   b.battle_seq AS battleSeq,
                   b.bvid AS bvid,
                   b.page_num AS pageNum,
                   m.match_stage_desc AS matchStageDesc,
                   m.start_time AS startTime,
                   m.camp1_team_name AS camp1TeamName,
                   m.camp1_score AS camp1Score,
                   m.camp2_team_name AS camp2TeamName,
                   m.camp2_score AS camp2Score,
                   bp.team_name AS teamName,
                   bp.player_name AS playerName,
                   bp.hero_id AS heroId,
                   bp.hero_name AS heroName,
                   CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END AS won,
                   bp.kill_num AS killNum,
                   bp.death_num AS deathNum,
                   bp.assist_num AS assistNum,
                   bp.gold AS gold,
                   bp.kda AS kda,
                   bp.mvp_score AS mvpScore,
                   bp.is_mvp AS isMvp,
                   bp.is_lose_mvp AS isLoseMvp,
                   ROUND(
                     IFNULL(bp.mvp_score, 0) * 1.8
                     + IFNULL(bp.kda, 0) * 1.35
                     + IFNULL(bp.kill_num, 0) * 0.8
                     + IFNULL(bp.assist_num, 0) * 0.25
                     - IFNULL(bp.death_num, 0) * 0.7
                     + CASE WHEN bp.camp = b.win_camp THEN 3 ELSE 0 END
                     + CASE WHEN bp.is_mvp = 1 THEN 4 ELSE 0 END
                     + CASE WHEN bp.is_lose_mvp = 1 THEN 1.5 ELSE 0 END
                   , 2) AS score
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE (bp.player_name LIKE CONCAT('%', #{playerName}, '%')
                OR SUBSTRING_INDEX(bp.player_name, '.', -1) = #{playerName})
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            ORDER BY score DESC, bp.kda DESC, m.start_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> playerFeaturedBattles(@Param("playerName") String playerName,
                                                    @Param("leagueId") String leagueId,
                                                    @Param("limit") int limit);

    /** 查询某英雄的高胜率选手 */
    @Select("""
            SELECT bp.player_name AS playerName, MAX(bp.team_name) AS teamName,
                   COUNT(*) AS games,
                   SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate,
                   ROUND(AVG(bp.kda), 2) AS avgKda,
                   MAX(ps.player_icon) AS playerIcon,
                   MAX(ts.team_icon) AS teamIcon
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            LEFT JOIN player_stats ps ON ps.player_name = SUBSTRING_INDEX(bp.player_name, '.', -1)
                AND ps.league_id = m.league_id
            LEFT JOIN team_stats ts ON ts.team_name = bp.team_name
                AND ts.league_id = m.league_id
            WHERE bp.hero_name = #{heroName}
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY bp.player_name
            HAVING games >= 2
            ORDER BY winRate DESC, games DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> heroPlayerStats(@Param("heroName") String heroName,
                                               @Param("leagueId") String leagueId,
                                               @Param("limit") int limit);

    @Select("""
            SELECT COUNT(DISTINCT SUBSTRING_INDEX(bp.player_name, '.', -1))
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE bp.hero_id = #{heroId}
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            """)
    Integer countHeroUsers(@Param("heroId") Integer heroId,
                           @Param("leagueId") String leagueId);

    @Select("""
            SELECT SUBSTRING_INDEX(bp.player_name, '.', -1) AS playerName,
                   MAX(bp.team_name) AS teamName,
                   COUNT(*) AS games,
                   SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate,
                   ROUND(AVG(bp.kda), 2) AS avgKda,
                   ROUND(AVG(bp.kill_num), 2) AS avgKill,
                   ROUND(AVG(bp.death_num), 2) AS avgDeath,
                   ROUND(AVG(bp.assist_num), 2) AS avgAssist,
                   MAX(ps.player_icon) AS playerIcon,
                   MAX(ts.team_icon) AS teamIcon
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            LEFT JOIN player_stats ps ON ps.player_name = SUBSTRING_INDEX(bp.player_name, '.', -1)
                AND ps.league_id = m.league_id
            LEFT JOIN team_stats ts ON ts.team_name = bp.team_name
                AND ts.league_id = m.league_id
            WHERE bp.hero_id = #{heroId}
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY SUBSTRING_INDEX(bp.player_name, '.', -1)
            HAVING games >= #{minGames}
            ORDER BY winRate DESC, games DESC, avgKda DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> heroPlayerLeaderboard(@Param("heroId") Integer heroId,
                                                    @Param("leagueId") String leagueId,
                                                    @Param("minGames") int minGames,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT bp.battle_id AS battleId,
                   b.match_id AS matchId,
                   b.battle_seq AS battleSeq,
                   b.bvid AS bvid,
                   b.page_num AS pageNum,
                   b.game_duration AS gameDuration,
                   m.match_stage AS matchStage,
                   m.match_stage_desc AS matchStageDesc,
                   m.start_time AS startTime,
                   m.camp1_team_name AS camp1TeamName,
                   m.camp1_score AS camp1Score,
                   m.camp2_team_name AS camp2TeamName,
                   m.camp2_score AS camp2Score,
                   bp.player_name AS playerName,
                   bp.team_name AS teamName,
                   bp.camp AS camp,
                   CASE WHEN bp.camp = b.win_camp THEN 1 ELSE 0 END AS won,
                   bp.kill_num AS killNum,
                   bp.death_num AS deathNum,
                   bp.assist_num AS assistNum,
                   bp.gold AS gold,
                   bp.kda AS kda,
                   bp.mvp_score AS mvpScore,
                   bp.is_mvp AS isMvp,
                   bp.position_desc AS positionDesc,
                   ROUND(
                     IFNULL(bp.mvp_score, 0) * 1.8
                     + IFNULL(bp.kda, 0) * 1.25
                     + IFNULL(bp.kill_num, 0) * 0.9
                     + IFNULL(bp.assist_num, 0) * 0.28
                     - IFNULL(bp.death_num, 0) * 0.65
                     + CASE WHEN bp.camp = b.win_camp THEN 3 ELSE 0 END
                     + CASE WHEN bp.is_mvp = 1 THEN 4 ELSE 0 END
                     + IFNULL(bp.hurt_to_hero_total_rate, 0) * 8
                   , 2) AS score
            FROM battle_player bp
            JOIN battle b ON bp.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE bp.hero_id = #{heroId}
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            ORDER BY score DESC, bp.kda DESC, m.start_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> heroFeaturedBattles(@Param("heroId") Integer heroId,
                                                  @Param("leagueId") String leagueId,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT ally.hero_id AS heroId,
                   MAX(ally.hero_name) AS heroName,
                   MAX(ally.position_desc) AS positionDesc,
                   COUNT(*) AS games,
                   SUM(CASE WHEN self.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN self.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate
            FROM battle_player self
            JOIN battle_player ally ON ally.battle_id = self.battle_id
                AND ally.camp = self.camp
                AND ally.hero_id <> self.hero_id
            JOIN battle b ON self.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE self.hero_id = #{heroId}
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY ally.hero_id
            HAVING games >= #{minGames}
            ORDER BY winRate DESC, games DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> heroSynergy(@Param("heroId") Integer heroId,
                                          @Param("leagueId") String leagueId,
                                          @Param("minGames") int minGames,
                                          @Param("limit") int limit);

    @Select("""
            SELECT enemy.hero_id AS heroId,
                   MAX(enemy.hero_name) AS heroName,
                   MAX(enemy.position_desc) AS positionDesc,
                   COUNT(*) AS games,
                   SUM(CASE WHEN self.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN self.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate
            FROM battle_player self
            JOIN battle_player enemy ON enemy.battle_id = self.battle_id
                AND enemy.camp <> self.camp
            JOIN battle b ON self.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE self.hero_id = #{heroId}
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY enemy.hero_id
            HAVING games >= #{minGames}
            ORDER BY winRate DESC, games DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> heroFavoredCounters(@Param("heroId") Integer heroId,
                                                  @Param("leagueId") String leagueId,
                                                  @Param("minGames") int minGames,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT enemy.hero_id AS heroId,
                   MAX(enemy.hero_name) AS heroName,
                   MAX(enemy.position_desc) AS positionDesc,
                   COUNT(*) AS games,
                   SUM(CASE WHEN self.camp = b.win_camp THEN 1 ELSE 0 END) AS wins,
                   ROUND(SUM(CASE WHEN self.camp = b.win_camp THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS winRate
            FROM battle_player self
            JOIN battle_player enemy ON enemy.battle_id = self.battle_id
                AND enemy.camp <> self.camp
            JOIN battle b ON self.battle_id = b.battle_id
            JOIN `match` m ON b.match_id = m.match_id
            WHERE self.hero_id = #{heroId}
              AND (#{leagueId} IS NULL OR m.league_id = #{leagueId})
            GROUP BY enemy.hero_id
            HAVING games >= #{minGames}
            ORDER BY winRate ASC, games DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> heroToughCounters(@Param("heroId") Integer heroId,
                                                @Param("leagueId") String leagueId,
                                                @Param("minGames") int minGames,
                                                @Param("limit") int limit);
}
