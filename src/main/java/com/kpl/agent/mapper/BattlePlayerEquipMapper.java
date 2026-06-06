package com.kpl.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kpl.agent.entity.BattlePlayerEquip;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.jdbc.SQL;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface BattlePlayerEquipMapper extends BaseMapper<BattlePlayerEquip> {

    /** 查询某装备在各分路的出场次数 */
    @Select("""
            SELECT bp.position AS positionNum, bp.position_desc AS positionDesc, COUNT(*) AS cnt
            FROM battle_player_equip e
            JOIN battle_player bp ON e.battle_id = bp.battle_id AND e.player_name = bp.player_name
            WHERE e.equip_id = #{equipId}
              AND (#{leagueId} IS NULL OR e.league_id = #{leagueId})
            GROUP BY bp.position, bp.position_desc
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

    /** 批量查询多件装备的分路分布 */
    @SelectProvider(type = BatchSql.class, method = "countByPositionBatch")
    List<Map<String, Object>> countByPositionBatch(@Param("equipIds") Set<Integer> equipIds, @Param("leagueId") String leagueId, @Param("heroId") Integer heroId, @Param("playerName") String playerName);

    /** 批量查询多件装备的平均出装顺序 */
    @SelectProvider(type = BatchSql.class, method = "avgOrderByEquipIds")
    List<Map<String, Object>> avgOrderByEquipIds(@Param("equipIds") Set<Integer> equipIds, @Param("leagueId") String leagueId);

    class BatchSql {
        public String countByPositionBatch(@Param("equipIds") Set<Integer> equipIds, @Param("leagueId") String leagueId, @Param("heroId") Integer heroId, @Param("playerName") String playerName) {
            return new SQL() {{
                SELECT("e.equip_id AS equipId", "bp.position AS positionNum", "COUNT(*) AS cnt");
                FROM("battle_player_equip e");
                JOIN("battle_player bp ON e.battle_id = bp.battle_id AND e.player_name = bp.player_name");
                WHERE("e.equip_id IN (" + joinIds(equipIds) + ")");
                if (leagueId != null && !leagueId.isBlank()) {
                    WHERE("e.league_id = '" + leagueId + "'");
                }
                if (heroId != null) {
                    WHERE("e.hero_id = " + heroId);
                }
                if (playerName != null && !playerName.isBlank()) {
                    WHERE("e.player_name = '" + playerName.replace("'", "''") + "'");
                }
                GROUP_BY("e.equip_id", "bp.position");
                ORDER_BY("e.equip_id", "cnt DESC");
            }}.toString();
        }

        public String avgOrderByEquipIds(@Param("equipIds") Set<Integer> equipIds, @Param("leagueId") String leagueId) {
            return new SQL() {{
                SELECT("equip_id AS equipId", "ROUND(AVG(equip_order), 1) AS avgOrder");
                FROM("battle_player_equip");
                WHERE("equip_id IN (" + joinIds(equipIds) + ")");
                WHERE("equip_order > 0");
                if (leagueId != null && !leagueId.isBlank()) {
                    AND().WHERE("league_id = '" + leagueId + "'");
                }
                GROUP_BY("equip_id");
            }}.toString();
        }

        private String joinIds(Set<Integer> ids) {
            StringBuilder sb = new StringBuilder();
            for (Integer id : ids) {
                if (sb.length() > 0) sb.append(',');
                sb.append(id);
            }
            return sb.toString();
        }
    }
}
