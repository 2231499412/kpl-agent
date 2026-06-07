package com.kpl.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.BattlePlayerEquip;
import com.kpl.agent.entity.EquipBaseInfo;
import com.kpl.agent.entity.HeroStats;
import com.kpl.agent.entity.Match;
import com.kpl.agent.mapper.BattlePlayerEquipMapper;
import com.kpl.agent.mapper.EquipBaseInfoMapper;
import com.kpl.agent.mapper.HeroStatsMapper;
import com.kpl.agent.mapper.MatchMapper;
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
    private final EquipBaseInfoMapper equipBaseInfoMapper;
    private final HeroStatsMapper heroStatsMapper;
    private final MatchMapper matchMapper;
    private final QueryCacheService queryCacheService;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /** 装备外号 → 正式名映射 */
    private static final Map<String, String> EQUIP_NICKNAMES = Map.ofEntries(
            Map.entry("黑切", "暗影战斧"),
            Map.entry("复活甲", "贤者的庇护"),
            Map.entry("名刀", "名刀·司命"),
            Map.entry("破晓", "破晓"),
            Map.entry("冰心", "极寒风暴"),
            Map.entry("绿甲", "不死鸟之眼"),
            Map.entry("霸者", "霸者重装"),
            Map.entry("反甲", "反伤刺甲"),
            Map.entry("不祥", "不祥征兆"),
            Map.entry("魔女", "魔女斗篷"),
            Map.entry("血魔", "血魔之怒"),
            Map.entry("金身", "辉月"),
            Map.entry("电刀", "闪电匕首"),
            Map.entry("无尽", "无尽战刃"),
            Map.entry("破军", "破军"),
            Map.entry("碎星锤", "碎星锤"),
            Map.entry("宗师", "宗师之力"),
            Map.entry("逐日", "逐日之弓"),
            Map.entry("穿云弓", "穿云弓"),
            Map.entry("末世", "末世"),
            Map.entry("制裁", "制裁之刃"),
            Map.entry("梦魇", "梦魇之牙"),
            Map.entry("帽子", "博学者之怒"),
            Map.entry("大书", "虚无法杖"),
            Map.entry("面具", "痛苦面具"),
            Map.entry("回响", "回响之杖"),
            Map.entry("冰杖", "凝冰之息"),
            Map.entry("时之预言", "时之预言"),
            Map.entry("圣杯", "圣杯"),
            Map.entry("炽热", "炽热支配者"),
            Map.entry("近卫", "近卫荣耀"),
            Map.entry("极影", "极影"),
            Map.entry("救赎", "救赎之翼"),
            Map.entry("奔狼", "奔狼纹章"),
            Map.entry("形昭", "形昭之鉴"),
            Map.entry("星泉", "星泉"),
            Map.entry("出影", "出影"),
            Map.entry("怒魂", "怒魂"),
            Map.entry("永夜", "永夜守护"),
            Map.entry("天穹", "天穹"),
            Map.entry("日渊", "日渊"),
            Map.entry("凛冬", "凛冬"),
            Map.entry("破魔", "破魔刀"),
            Map.entry("抵抗鞋", "抵抗之靴"),
            Map.entry("布甲鞋", "影忍之足"),
            Map.entry("CD鞋", "冷静之靴"),
            Map.entry("攻速鞋", "急速战靴"),
            Map.entry("法穿鞋", "秘法之靴"),
            Map.entry("疾步鞋", "疾步之靴"),
            Map.entry("韧性鞋", "抵抗之靴")
    );

    /** 查询全局装备出场排行（按赛事） */
    public Map<String, Object> queryTopGlobal(String leagueId, int limit) {
        String key = "kpl:%s:equip:global:%d".formatted(leagueId, limit);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            LambdaQueryWrapper<BattlePlayerEquip> qw = new LambdaQueryWrapper<BattlePlayerEquip>();
            if (leagueId != null && !leagueId.isBlank()) {
                qw.eq(BattlePlayerEquip::getLeagueId, leagueId);
            }
            List<BattlePlayerEquip> equips = battlePlayerEquipMapper.selectList(qw);
            return buildEquipResult("equip_global", "装备总榜", equips, limit, leagueId);
        });
    }

    /** 按装备名搜索（支持外号） */
    public Map<String, Object> queryByName(String equipName, String leagueId, int limit) {
        // 外号解析
        String resolved = EQUIP_NICKNAMES.getOrDefault(equipName, equipName);

        String key = "kpl:%s:equip:search:%s:%d".formatted(leagueId, resolved, limit);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            // 先精确匹配，再模糊匹配
            LambdaQueryWrapper<BattlePlayerEquip> qw = new LambdaQueryWrapper<BattlePlayerEquip>()
                    .like(BattlePlayerEquip::getEquipName, resolved);
            if (leagueId != null && !leagueId.isBlank()) {
                qw.eq(BattlePlayerEquip::getLeagueId, leagueId);
            }
            qw.last("LIMIT 10000");
            List<BattlePlayerEquip> equips = battlePlayerEquipMapper.selectList(qw);
            return buildEquipResult("equip_search", equipName, equips, limit, leagueId);
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
        if (hs == null) return buildEquipResult("hero_equip", heroName, List.of(), limit, leagueId);
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
            return buildEquipResult("hero_equip", "hero_" + heroId, equips, limit, leagueId, heroId, null);
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
            return buildEquipResult("player_equip", playerName, equips, limit, leagueId, null, playerName);
        });
    }

    private Map<String, Object> buildEquipResult(String type, String keyword, List<BattlePlayerEquip> equips, int topN, String leagueId) {
        return buildEquipResult(type, keyword, equips, topN, leagueId, null, null);
    }

    private Map<String, Object> buildEquipResult(String type, String keyword, List<BattlePlayerEquip> equips, int topN, String leagueId, Integer heroId, String playerName) {
        Map<Integer, EquipInfo> countMap = new LinkedHashMap<>();
        for (BattlePlayerEquip e : equips) {
            EquipInfo info = countMap.computeIfAbsent(e.getEquipId(), id -> new EquipInfo(id, e.getEquipName(), e.getEquipIcon()));
            info.count++;
            if (e.getHeroId() != null) info.heroIds.add(e.getHeroId());
            if (e.getPlayerName() != null) info.playerNames.add(e.getPlayerName());
        }

        // 查总比赛场数，每场约5局
        long matchCount = matchMapper.selectCount(
                new LambdaQueryWrapper<Match>()
                        .eq(Match::getStatus, 2)
                        .eq(leagueId != null && !leagueId.isBlank(), Match::getLeagueId, leagueId));
        int totalGames = (int) Math.max(matchCount * 5, 1);

        // 统计每件装备出现在多少局（按 battleId 去重，同一局多人买只算一次）
        Map<Integer, Set<String>> equipGameMap = new HashMap<>();
        for (BattlePlayerEquip e : equips) {
            equipGameMap.computeIfAbsent(e.getEquipId(), k -> new HashSet<>())
                    .add(e.getBattleId());
        }

        List<Map<String, Object>> data = countMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.count, a.count))
                .limit(topN)
                .map(e -> {
                    int gameCount = equipGameMap.getOrDefault(e.equipId, Set.of()).size();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("equipId", e.equipId);
                    String cleanedName = cleanSurrogates(e.equipName);
                    if (!cleanedName.equals(e.equipName)) {
                        log.info("cleanSurrogates applied: '{}' -> '{}'", e.equipName, cleanedName);
                    }
                    row.put("equipName", cleanedName);
                    row.put("equipIcon", e.equipIcon);
                    row.put("pickCount", e.count);
                    row.put("heroCount", e.heroIds.size());
                    row.put("playerCount", e.playerNames.size());
                    row.put("pickRate", Math.round(gameCount * 10000.0 / totalGames) / 10000.0);
                    return row;
                })
                .collect(Collectors.toList());

        // 批量查询分路分布和平均出装顺序
        Set<Integer> topEquipIds = data.stream()
                .map(r -> (Integer) r.get("equipId"))
                .collect(Collectors.toSet());

        if (!topEquipIds.isEmpty()) {
            // 分路分布：按 equipId 分组（用 position 数字映射中文名，避免数据库编码问题）
            Map<Integer, String> posNameMap = Map.of(2, "中路", 4, "发育路", 5, "对抗路", 6, "游走", 7, "打野");
            Map<Integer, List<Map<String, Object>>> posMap = new HashMap<>();
            try {
                List<Map<String, Object>> posRows = battlePlayerEquipMapper.countByPositionBatch(topEquipIds, leagueId, heroId, playerName);
                for (Map<String, Object> pr : posRows) {
                    int eid = ((Number) pr.get("equipId")).intValue();
                    // 合并同名分路（如 1 和 6 都是"对抗路"）
                    String posName = posNameMap.getOrDefault(((Number) pr.get("positionNum")).intValue(), "其他");
                    pr.put("positionDesc", posName);
                    posMap.computeIfAbsent(eid, k -> new ArrayList<>()).add(pr);
                }
                // 合并同名分路的计数
                for (Map.Entry<Integer, List<Map<String, Object>>> entry : posMap.entrySet()) {
                    Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
                    for (Map<String, Object> pr : entry.getValue()) {
                        String name = (String) pr.get("positionDesc");
                        Map<String, Object> existing = merged.get(name);
                        if (existing != null) {
                            existing.put("cnt", ((Number) existing.get("cnt")).intValue() + ((Number) pr.get("cnt")).intValue());
                        } else {
                            merged.put(name, pr);
                        }
                    }
                    entry.setValue(new ArrayList<>(merged.values()));
                    entry.getValue().sort((a, b) -> Integer.compare(((Number) b.get("cnt")).intValue(), ((Number) a.get("cnt")).intValue()));
                }
            } catch (Exception ignored) {}

            // 平均出装顺序
            Map<Integer, Double> avgOrderMap = new HashMap<>();
            try {
                List<Map<String, Object>> avgRows = battlePlayerEquipMapper.avgOrderByEquipIds(topEquipIds, leagueId);
                for (Map<String, Object> ar : avgRows) {
                    int eid = ((Number) ar.get("equipId")).intValue();
                    double avg = ((Number) ar.get("avgOrder")).doubleValue();
                    avgOrderMap.put(eid, avg);
                }
            } catch (Exception ignored) {}

            // 装备价格
            Map<Integer, EquipBaseInfo> priceMap = new HashMap<>();
            try {
                List<EquipBaseInfo> infos = equipBaseInfoMapper.selectList(
                        new LambdaQueryWrapper<EquipBaseInfo>().in(EquipBaseInfo::getItemId, topEquipIds));
                for (EquipBaseInfo info : infos) {
                    priceMap.put(info.getItemId(), info);
                }
            } catch (Exception ignored) {}

            for (Map<String, Object> row : data) {
                int eid = (Integer) row.get("equipId");
                row.put("positions", posMap.getOrDefault(eid, List.of()));
                Double avgOrder = avgOrderMap.get(eid);
                row.put("avgOrder", avgOrder != null ? avgOrder : null);
                EquipBaseInfo priceInfo = priceMap.get(eid);
                if (priceInfo != null) {
                    row.put("totalPrice", priceInfo.getTotalPrice());
                    row.put("price", priceInfo.getPrice());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("keyword", keyword);
        result.put("count", data.size());
        result.put("totalGames", totalGames);
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
                result.put("equipName", cleanSurrogates(sample.getEquipName()));
                result.put("equipIcon", sample.getEquipIcon());
                result.put("equipDescGain", cleanSurrogates(sample.getEquipDescGain()));
                result.put("equipDescFunction", cleanSurrogates(sample.getEquipDescFunction()));
            }

            // 价格信息 + 被动效果
            try {
                EquipBaseInfo priceInfo = equipBaseInfoMapper.selectOne(
                        new LambdaQueryWrapper<EquipBaseInfo>()
                                .eq(EquipBaseInfo::getItemId, equipId)
                                .last("LIMIT 1"));
                if (priceInfo != null) {
                    result.put("totalPrice", priceInfo.getTotalPrice());
                    result.put("price", priceInfo.getPrice());
                    if (priceInfo.getDes1() != null && !priceInfo.getDes1().isEmpty()) {
                        result.put("equipDescGain", cleanSurrogates(priceInfo.getDes1()));
                    }
                    if (priceInfo.getDes2() != null && !priceInfo.getDes2().isEmpty()) {
                        result.put("equipPassive", cleanSurrogates(priceInfo.getDes2()));
                    }
                }
            } catch (Exception ignored) {}

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

    /** 清理字符串中的 UTF-16 代理对字符（低代理 U+DC00-U+DFFF） */
    private static String cleanSurrogates(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLowSurrogate(c)) {
                // 跳过孤立的低代理字符（前面没有高代理）
                if (i > 0 && Character.isHighSurrogate(s.charAt(i - 1))) {
                    sb.append(c);
                }
                // 否则丢弃这个孤立的低代理
            } else if (Character.isHighSurrogate(c)) {
                // 检查下一个字符是否是低代理
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    sb.append(c); // 保留，下一个循环会追加低代理
                }
                // 否则丢弃孤立的高代理
            } else {
                sb.append(c);
            }
        }
        if (sb.length() != s.length()) {
            log.debug("cleanSurrogates: '{}' (len={}) -> '{}' (len={})", s, s.length(), sb, sb.length());
        }
        return sb.toString();
    }
}
