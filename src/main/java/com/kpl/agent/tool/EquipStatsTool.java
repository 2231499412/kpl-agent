package com.kpl.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.BattlePlayerEquip;
import com.kpl.agent.entity.HeroStats;
import com.kpl.agent.mapper.BattlePlayerEquipMapper;
import com.kpl.agent.mapper.HeroStatsMapper;
import com.kpl.agent.service.QueryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 装备数据查询工具：按英雄/选手/赛事查询常用出装
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EquipStatsTool {

    private final BattlePlayerEquipMapper battlePlayerEquipMapper;
    private final HeroStatsMapper heroStatsMapper;
    private final QueryCacheService queryCacheService;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /** 查询全局装备出场排行（按赛事） */
    public Map<String, Object> queryTopGlobal(String leagueId, int limit) {
        String key = "kpl:%s:equip:global:%d".formatted(leagueId, limit);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            LambdaQueryWrapper<BattlePlayerEquip> qw = new LambdaQueryWrapper<BattlePlayerEquip>();
            if (leagueId != null && !leagueId.isBlank()) {
                qw.eq(BattlePlayerEquip::getLeagueId, leagueId);
            }
            qw.last("LIMIT 10000");
            List<BattlePlayerEquip> equips = battlePlayerEquipMapper.selectList(qw);
            return buildEquipResult("equip_global", "装备总榜", equips, limit);
        });
    }

    /** 查询某英雄的常用装备（按赛事） */
    public Map<String, Object> queryByHero(String heroName, String leagueId, int limit) {
        // 先查 hero_id，再按 hero_id 查装备
        HeroStats hs = heroStatsMapper.selectOne(
                new LambdaQueryWrapper<HeroStats>()
                        .eq(HeroStats::getLeagueId, leagueId)
                        .like(HeroStats::getHeroName, heroName)
                        .last("LIMIT 1"));
        if (hs == null) return buildEquipResult("hero_equip", heroName, List.of(), limit);
        return queryByHeroId(hs.getHeroId(), leagueId, limit);
    }

    /** 查询某英雄的常用装备（按英雄ID，按赛事） */
    public Map<String, Object> queryByHeroId(Integer heroId, String leagueId, int limit) {
        String key = "kpl:%s:equip:heroId:%d:%d".formatted(leagueId, heroId, limit);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            LambdaQueryWrapper<BattlePlayerEquip> qw = new LambdaQueryWrapper<BattlePlayerEquip>()
                    .eq(BattlePlayerEquip::getHeroId, heroId);
            if (leagueId != null && !leagueId.isBlank()) {
                qw.eq(BattlePlayerEquip::getLeagueId, leagueId);
            }
            qw.last("LIMIT 2000");
            List<BattlePlayerEquip> equips = battlePlayerEquipMapper.selectList(qw);
            return buildEquipResult("hero_equip", "hero_" + heroId, equips, limit);
        });
    }

    /** 查询某选手的常用装备（按赛事） */
    public Map<String, Object> queryByPlayer(String playerName, String leagueId, int limit) {
        String key = "kpl:%s:equip:player:%s:%d".formatted(leagueId, playerName, limit);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            LambdaQueryWrapper<BattlePlayerEquip> qw = new LambdaQueryWrapper<BattlePlayerEquip>()
                    .like(BattlePlayerEquip::getPlayerName, playerName);
            if (leagueId != null && !leagueId.isBlank()) {
                qw.eq(BattlePlayerEquip::getLeagueId, leagueId);
            }
            qw.last("LIMIT 2000");
            List<BattlePlayerEquip> equips = battlePlayerEquipMapper.selectList(qw);
            return buildEquipResult("player_equip", playerName, equips, limit);
        });
    }

    private Map<String, Object> buildEquipResult(String type, String keyword, List<BattlePlayerEquip> equips, int topN) {
        Map<Integer, EquipInfo> countMap = new LinkedHashMap<>();
        for (BattlePlayerEquip e : equips) {
            EquipInfo info = countMap.computeIfAbsent(e.getEquipId(), id -> new EquipInfo(id, e.getEquipName(), e.getEquipIcon()));
            info.count++;
            if (e.getHeroId() != null) info.heroIds.add(e.getHeroId());
            if (e.getPlayerName() != null) info.playerNames.add(e.getPlayerName());
        }

        // totalPicks = 总出场记录数，每人6件装备，所以总人数 = totalPicks/6
        int totalPicks = equips.size();
        int totalPlayers = Math.max(totalPicks / 6, 1);

        List<Map<String, Object>> data = countMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.count, a.count))
                .limit(topN)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("equipId", e.equipId);
                    row.put("equipName", e.equipName);
                    row.put("equipIcon", e.equipIcon);
                    row.put("pickCount", e.count);
                    row.put("heroCount", e.heroIds.size());
                    row.put("playerCount", e.playerNames.size());
                    row.put("pickRate", Math.round(e.count * 10000.0 / totalPlayers) / 10000.0);
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("keyword", keyword);
        result.put("count", data.size());
        result.put("totalGames", totalPicks / 60); // 每局10人×6装备=60条记录
        result.put("data", data);
        return result;
    }

    /** 查询单件装备的详细统计：分路分布、英雄分布、总览 */
    public Map<String, Object> queryDetail(int equipId, String leagueId, int heroLimit) {
        String key = "kpl:%s:equip:detail:%d:%d".formatted(leagueId, equipId, heroLimit);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            Map<String, Object> result = new LinkedHashMap<>();

            // 基本信息（从已有数据取名称和图标）
            BattlePlayerEquip sample = battlePlayerEquipMapper.selectOne(
                    new LambdaQueryWrapper<BattlePlayerEquip>()
                            .eq(BattlePlayerEquip::getEquipId, equipId)
                            .last("LIMIT 1"));
            if (sample != null) {
                result.put("equipId", equipId);
                result.put("equipName", sample.getEquipName());
                result.put("equipIcon", sample.getEquipIcon());
                result.put("equipDescGain", sample.getEquipDescGain());
                result.put("equipDescFunction", sample.getEquipDescFunction());
            }

            // 总览
            Map<String, Object> summary = battlePlayerEquipMapper.equipSummary(equipId, leagueId);
            result.put("summary", summary);

            // 分路分布
            List<Map<String, Object>> positions = battlePlayerEquipMapper.countByPosition(equipId, leagueId);
            result.put("positions", positions);

            // 英雄分布
            List<Map<String, Object>> heroes = battlePlayerEquipMapper.countByHero(equipId, leagueId, heroLimit);
            result.put("heroes", heroes);

            return result;
        });
    }

    private static class EquipInfo {
        int equipId;
        String equipName;
        String equipIcon;
        int count = 0;
        Set<Integer> heroIds = new HashSet<>();
        Set<String> playerNames = new HashSet<>();

        EquipInfo(int equipId, String equipName, String equipIcon) {
            this.equipId = equipId;
            this.equipName = equipName;
            this.equipIcon = equipIcon;
        }
    }
}
