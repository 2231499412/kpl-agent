package com.kpl.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kpl.agent.entity.BattlePlayerEquip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BattlePlayerEquipMapper extends BaseMapper<BattlePlayerEquip> {

    /** 查询某装备在各分路的出场次数 */
    @Select("""
            SELECT bp.position_desc AS positionDesc, COUNT(*) AS cnt
            FROM battle_player_equip e
            JOIN battle_player bp ON e.battle_id = bp.battle_id AND e.player_name = bp.player_name
            WHERE e.equip_id = #{equipId}
              AND (#{leagueId} IS NULL OR e.league_id = #{leagueId})
            GROUP BY bp.position_desc
            ORDER BY cnt DESC
            """)
    List<Map<String, Object>> countByPosition(@Param("equipId") int equipId, @Param("leagueId") String leagueId);

    /** 查询某装备在各英雄的出场次数 */
    @Select("""
            SELECT e.hero_id AS heroId, MAX(h.hero_name) AS heroName, COUNT(*) AS cnt
            FROM battle_player_equip e
            LEFT JOIN hero_stats h ON e.hero_id = h.hero_id AND (#{leagueId} IS NULL OR h.league_id = #{leagueId})
            WHERE e.equip_id = #{equipId}
              AND (#{leagueId} IS NULL OR e.league_id = #{leagueId})
            GROUP BY e.hero_id
            ORDER BY cnt DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countByHero(@Param("equipId") int equipId, @Param("leagueId") String leagueId, @Param("limit") int limit);

    /** 查询某装备的总出场次数和使用的不同选手数 */
    @Select("""
            SELECT COUNT(*) AS totalPick, COUNT(DISTINCT e.player_name) AS playerCount, COUNT(DISTINCT e.hero_id) AS heroCount
            FROM battle_player_equip e
            WHERE e.equip_id = #{equipId}
              AND (#{leagueId} IS NULL OR e.league_id = #{leagueId})
            """)
    Map<String, Object> equipSummary(@Param("equipId") int equipId, @Param("leagueId") String leagueId);
}
