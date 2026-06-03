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
}
