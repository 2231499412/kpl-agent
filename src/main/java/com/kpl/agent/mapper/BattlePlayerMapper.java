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
