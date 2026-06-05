package com.kpl.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.Battle;
import com.kpl.agent.entity.BattlePlayer;
import com.kpl.agent.entity.BattlePlayerEquip;
import com.kpl.agent.entity.League;
import com.kpl.agent.entity.Match;
import com.kpl.agent.entity.PlayerStats;
import com.kpl.agent.entity.TeamStats;
import com.kpl.agent.mapper.BattleMapper;
import com.kpl.agent.mapper.BattlePlayerEquipMapper;
import com.kpl.agent.mapper.BattlePlayerMapper;
import com.kpl.agent.mapper.LeagueMapper;
import com.kpl.agent.mapper.MatchMapper;
import com.kpl.agent.mapper.PlayerStatsMapper;
import com.kpl.agent.mapper.TeamStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LaneRadarService {

    private static final int MIN_SAMPLE_SIZE = 50;
    private static final Map<String, Integer> ROLE_POSITIONS = Map.of(
            "对抗路", 6,
            "打野", 5,
            "中路", 2,
            "发育路", 7,
            "游走", 4
    );
    private static final Set<String> CARRY_ROLES = Set.of("中路", "发育路");

    private final LeagueMapper leagueMapper;
    private final MatchMapper matchMapper;
    private final BattleMapper battleMapper;
    private final BattlePlayerMapper battlePlayerMapper;
    private final BattlePlayerEquipMapper battlePlayerEquipMapper;
    private final PlayerStatsMapper playerStatsMapper;
    private final TeamStatsMapper teamStatsMapper;

    public Map<String, Object> buildRadar(String leagueId, String matchId, String battleId, String role) {
        String resolvedRole = normalizeRole(role);
        Integer position = ROLE_POSITIONS.get(resolvedRole);
        if (position == null) {
            return Map.of("error", "不支持的分路: " + role);
        }

        Match match = selectMatch(matchId);
        Battle battle = selectBattle(battleId);
        if (match == null) {
            return Map.of("error", "比赛不存在: " + matchId);
        }
        if (battle == null) {
            return Map.of("error", "小局不存在: " + battleId);
        }
        String resolvedLeagueId = leagueId != null && !leagueId.isBlank() ? leagueId : match.getLeagueId();
        League league = selectLeague(resolvedLeagueId);

        List<BattlePlayer> currentPlayers = playersByBattle(battle.getBattleId());
        List<RadarSample> currentSamples = samplesFromBattle(match, battle, currentPlayers, position);
        RadarSample blue = currentSamples.stream().filter(s -> Objects.equals(s.player().getCamp(), 1)).findFirst().orElse(null);
        RadarSample red = currentSamples.stream().filter(s -> Objects.equals(s.player().getCamp(), 2)).findFirst().orElse(null);
        if (blue == null || red == null) {
            return Map.of("error", "该小局缺少双方同分路选手数据");
        }

        Map<String, List<BattlePlayerEquip>> equipMap = battlePlayerEquipMapper.selectList(
                        new LambdaQueryWrapper<BattlePlayerEquip>().eq(BattlePlayerEquip::getBattleId, battle.getBattleId()))
                .stream()
                .collect(Collectors.groupingBy(BattlePlayerEquip::getPlayerName));

        SamplePool pool = selectSamplePool(resolvedLeagueId, position);
        List<MetricDefinition> definitions = metricDefinitions(resolvedRole);
        List<Map<String, Object>> indicators = definitions.stream()
                .map(def -> Map.<String, Object>of("key", def.key(), "name", def.name(), "max", 100))
                .toList();

        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("type", pool.type());
        basis.put("label", pool.label());
        basis.put("leagueIds", pool.leagueIds());
        basis.put("sampleCount", pool.samples().size());
        basis.put("minRequired", MIN_SAMPLE_SIZE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "lane_radar");
        result.put("league", league);
        result.put("match", match);
        result.put("battle", battle);
        result.put("role", resolvedRole);
        result.put("basis", basis);
        result.put("indicators", indicators);
        result.put("blue", buildSide(blue, definitions, pool, resolvedLeagueId, equipMap));
        result.put("red", buildSide(red, definitions, pool, resolvedLeagueId, equipMap));
        return result;
    }

    private Map<String, Object> buildSide(RadarSample sample, List<MetricDefinition> definitions, SamplePool pool,
                                          String leagueId, Map<String, List<BattlePlayerEquip>> equipMap) {
        Map<String, Object> side = new LinkedHashMap<>();
        BattlePlayer player = sample.player();
        PlayerStats playerStats = selectPlayerStats(leagueId, player.getPlayerName());
        TeamStats teamStats = selectTeamStats(leagueId, player.getTeamName());

        side.put("camp", player.getCamp());
        side.put("teamId", player.getTeamId());
        side.put("teamName", player.getTeamName());
        side.put("teamIcon", teamStats == null ? null : teamStats.getTeamIcon());
        side.put("playerName", player.getPlayerName());
        side.put("playerIcon", playerStats == null ? null : playerStats.getPlayerIcon());
        side.put("heroId", player.getHeroId());
        side.put("heroName", player.getHeroName());
        side.put("position", player.getPosition());
        side.put("positionDesc", player.getPositionDesc());
        side.put("summonerAbilityId", player.getSummonerAbilityId());
        side.put("summonerAbilityName", player.getSummonerAbilityName());
        side.put("summonerAbilityIcon", summonerAbilityIcon(player.getSummonerAbilityId()));
        side.put("kills", safeInt(player.getKillNum()));
        side.put("deaths", safeInt(player.getDeathNum()));
        side.put("assists", safeInt(player.getAssistNum()));
        side.put("gold", safeInt(player.getGold()));
        side.put("damage", playerDamage(sample));
        side.put("beHurt", playerBeHurt(sample));
        side.put("durationMinutes", round(sample.minutes(), 2));
        side.put("isMvp", safeInt(player.getIsMvp()));
        side.put("isLoseMvp", safeInt(player.getIsLoseMvp()));
        side.put("symbolIds", player.getSymbolIds());
        side.put("equips", buildEquips(equipMap.getOrDefault(player.getPlayerName(), List.of())));
        side.put("metrics", definitions.stream().map(def -> buildMetric(def, sample, pool)).toList());
        return side;
    }

    private String summonerAbilityIcon(Integer abilityId) {
        if (abilityId == null || abilityId <= 0) {
            return null;
        }
        return "https://game.gtimg.cn/images/yxzj/img201606/summoner/" + abilityId + ".jpg";
    }

    private List<Map<String, Object>> buildEquips(List<BattlePlayerEquip> equips) {
        return equips.stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("equipId", e.getEquipId());
                    row.put("equipName", e.getEquipName());
                    row.put("equipIcon", e.getEquipIcon());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> buildMetric(MetricDefinition def, RadarSample sample, SamplePool pool) {
        double raw = def.reader().apply(sample);
        double displayRaw = def.displayReader().apply(sample);
        double score = percentile(raw, pool.samples().stream().map(def.reader()).toList());
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("key", def.key());
        metric.put("name", def.name());
        metric.put("raw", round(raw, 4));
        metric.put("displayRaw", round(displayRaw, 4));
        metric.put("unit", def.unit());
        metric.put("displayUnit", def.displayUnit());
        metric.put("score", round(score, 1));
        metric.put("benchmarkLabel", pool.label());
        metric.put("sampleCount", pool.samples().size());
        metric.put("description", def.description());
        return metric;
    }

    private SamplePool selectSamplePool(String leagueId, int position) {
        List<RadarSample> leagueSamples = buildSamples(List.of(leagueId), position);
        if (leagueSamples.size() >= MIN_SAMPLE_SIZE) {
            return new SamplePool("league", "当前赛事同分路单局百分位", List.of(leagueId), leagueSamples);
        }

        List<String> recentLeagueIds = recentLeagueIds(leagueId);
        List<RadarSample> recentSamples = buildSamples(recentLeagueIds, position);
        if (recentSamples.size() >= MIN_SAMPLE_SIZE) {
            return new SamplePool("recent2", "近两个赛事同分路单局百分位", recentLeagueIds, recentSamples);
        }

        List<RadarSample> historySamples = buildSamples(List.of(), position);
        return new SamplePool("history", "历史全量同分路单局百分位", List.of(), historySamples);
    }

    private List<String> recentLeagueIds(String leagueId) {
        League current = selectLeague(leagueId);
        List<League> leagues = leagueMapper.selectList(new LambdaQueryWrapper<League>()
                .orderByDesc(League::getStartTime)
                .orderByDesc(League::getYear)
                .orderByDesc(League::getSeason)
                .orderByDesc(League::getLeagueId));
        if (current != null && current.getStartTime() != null) {
            LocalDateTime anchor = current.getStartTime();
            List<String> ids = leagues.stream()
                    .filter(l -> l.getStartTime() == null || !l.getStartTime().isAfter(anchor))
                    .map(League::getLeagueId)
                    .filter(Objects::nonNull)
                    .limit(2)
                    .toList();
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        return leagues.stream()
                .map(League::getLeagueId)
                .filter(Objects::nonNull)
                .limit(2)
                .toList();
    }

    private List<RadarSample> buildSamples(List<String> leagueIds, int position) {
        LambdaQueryWrapper<Match> matchQuery = new LambdaQueryWrapper<Match>().eq(Match::getStatus, 2);
        if (leagueIds != null && !leagueIds.isEmpty()) {
            matchQuery.in(Match::getLeagueId, leagueIds);
        }
        List<Match> matches = matchMapper.selectList(matchQuery);
        if (matches.isEmpty()) {
            return List.of();
        }
        Map<String, Match> matchMap = matches.stream()
                .filter(m -> m.getMatchId() != null)
                .collect(Collectors.toMap(Match::getMatchId, Function.identity(), (a, b) -> a));
        List<String> matchIds = new ArrayList<>(matchMap.keySet());
        List<Battle> battles = battleMapper.selectList(new LambdaQueryWrapper<Battle>()
                .in(Battle::getMatchId, matchIds));
        if (battles.isEmpty()) {
            return List.of();
        }
        List<String> battleIds = battles.stream().map(Battle::getBattleId).filter(Objects::nonNull).toList();
        List<BattlePlayer> players = battlePlayerMapper.selectList(new LambdaQueryWrapper<BattlePlayer>()
                .in(BattlePlayer::getBattleId, battleIds));
        Map<String, List<BattlePlayer>> playerMap = players.stream()
                .collect(Collectors.groupingBy(BattlePlayer::getBattleId));

        List<RadarSample> samples = new ArrayList<>();
        for (Battle battle : battles) {
            Match match = matchMap.get(battle.getMatchId());
            if (match == null) continue;
            samples.addAll(samplesFromBattle(match, battle, playerMap.getOrDefault(battle.getBattleId(), List.of()), position));
        }
        return samples;
    }

    private List<RadarSample> samplesFromBattle(Match match, Battle battle, List<BattlePlayer> players, int position) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        Map<Integer, TeamTotals> totals = players.stream()
                .filter(p -> p.getCamp() != null)
                .collect(Collectors.groupingBy(BattlePlayer::getCamp,
                        Collectors.collectingAndThen(Collectors.toList(), this::teamTotals)));
        double minutes = durationMinutes(battle.getGameDuration());
        List<RadarSample> samples = new ArrayList<>();
        for (BattlePlayer player : players) {
            if (!Objects.equals(player.getPosition(), position)) {
                continue;
            }
            TeamTotals teamTotals = totals.get(player.getCamp());
            if (teamTotals == null || minutes <= 0) {
                continue;
            }
            samples.add(new RadarSample(match, battle, player, teamTotals, minutes));
        }
        return samples;
    }

    private TeamTotals teamTotals(List<BattlePlayer> players) {
        long damage = players.stream().mapToLong(p -> safeLong(firstNonNull(p.getHurtToHero(), p.getHurtToHeroTotal(), p.getHurtTotal()))).sum();
        long beHurt = players.stream().mapToLong(p -> safeLong(firstNonNull(p.getBeHurtTotal(), p.getBeHurtByHeroTotal()))).sum();
        int kills = players.stream().mapToInt(p -> safeInt(p.getKillNum())).sum();
        return new TeamTotals(damage, beHurt, kills);
    }

    private List<MetricDefinition> metricDefinitions(String role) {
        List<MetricDefinition> defs = new ArrayList<>();
        defs.add(new MetricDefinition("damageShare", "伤害占比", "%", "%", s -> ratio(playerDamage(s), s.teamTotals().damage()), s -> ratio(playerDamage(s), s.teamTotals().damage()), "选手对英雄伤害 / 队伍对英雄伤害"));
        defs.add(new MetricDefinition("damagePerMinute", "分均伤害", "", "", s -> playerDamage(s) / s.minutes(), s -> playerDamage(s) / s.minutes(), "选手对英雄伤害 / 比赛时长分钟"));
        if (CARRY_ROLES.contains(role)) {
            defs.add(new MetricDefinition("survival", "生存", "", "次死亡", s -> 1.0 / (safeInt(s.player().getDeathNum()) + 1.0), s -> (double) safeInt(s.player().getDeathNum()), "评分使用 1 / (死亡数 + 1)，展示原始死亡数"));
        } else {
            defs.add(new MetricDefinition("beHurtShare", "承伤占比", "%", "%", s -> ratio(playerBeHurt(s), s.teamTotals().beHurt()), s -> ratio(playerBeHurt(s), s.teamTotals().beHurt()), "选手总承伤 / 队伍总承伤"));
        }
        defs.add(new MetricDefinition("participationRate", "参团率", "%", "%", s -> ratio(killParticipation(s), s.teamTotals().kills()), s -> ratio(killParticipation(s), s.teamTotals().kills()), "(击杀 + 助攻) / 队伍总击杀"));
        defs.add(new MetricDefinition("killParticipation", "参与击杀数", "次", "次", s -> (double) killParticipation(s), s -> (double) killParticipation(s), "击杀 + 助攻"));
        if (CARRY_ROLES.contains(role)) {
            defs.add(new MetricDefinition("damagePerGold", "经济转伤害", "", "", s -> ratio(playerDamage(s), safeInt(s.player().getGold())), s -> ratio(playerDamage(s), safeInt(s.player().getGold())), "选手对英雄伤害 / 选手总经济"));
        } else {
            defs.add(new MetricDefinition("beHurtPerDeath", "每死承伤", "", "", s -> playerBeHurt(s) / Math.max(safeInt(s.player().getDeathNum()), 1.0), s -> playerBeHurt(s) / Math.max(safeInt(s.player().getDeathNum()), 1.0), "选手总承伤 / max(死亡数, 1)"));
        }
        defs.add(new MetricDefinition("kda", "KDA", "", "", s -> killParticipation(s) / Math.max(safeInt(s.player().getDeathNum()), 1.0), s -> killParticipation(s) / Math.max(safeInt(s.player().getDeathNum()), 1.0), "(击杀 + 助攻) / max(死亡数, 1)"));
        defs.add(new MetricDefinition("goldPerMinute", "分均经济", "", "", s -> safeInt(s.player().getGold()) / s.minutes(), s -> safeInt(s.player().getGold()) / s.minutes(), "选手总经济 / 比赛时长分钟"));
        return defs;
    }

    private double percentile(double value, List<Double> values) {
        List<Double> valid = values.stream().filter(v -> v != null && Double.isFinite(v)).sorted().toList();
        if (valid.isEmpty() || !Double.isFinite(value)) {
            return 0;
        }
        long less = valid.stream().filter(v -> v < value).count();
        long equal = valid.stream().filter(v -> Objects.equals(v, value)).count();
        return Math.max(0, Math.min(100, ((less + equal * 0.5) / valid.size()) * 100.0));
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "对抗路";
        return switch (role.trim()) {
            case "边路", "上路", "对抗" -> "对抗路";
            case "野区", "打野位" -> "打野";
            case "中单", "中路位" -> "中路";
            case "射手", "发育", "发育路位" -> "发育路";
            case "辅助", "游走位" -> "游走";
            default -> role.trim();
        };
    }

    private Match selectMatch(String matchId) {
        return matchMapper.selectOne(new LambdaQueryWrapper<Match>().eq(Match::getMatchId, matchId).last("LIMIT 1"));
    }

    private Battle selectBattle(String battleId) {
        return battleMapper.selectOne(new LambdaQueryWrapper<Battle>().eq(Battle::getBattleId, battleId).last("LIMIT 1"));
    }

    private League selectLeague(String leagueId) {
        if (leagueId == null || leagueId.isBlank()) return null;
        return leagueMapper.selectOne(new LambdaQueryWrapper<League>().eq(League::getLeagueId, leagueId).last("LIMIT 1"));
    }

    private PlayerStats selectPlayerStats(String leagueId, String playerName) {
        if (playerName == null || playerName.isBlank()) return null;
        String shortName = shortPlayerName(playerName);
        return playerStatsMapper.selectOne(new LambdaQueryWrapper<PlayerStats>()
                .eq(PlayerStats::getLeagueId, leagueId)
                .and(w -> w.eq(PlayerStats::getPlayerName, playerName)
                        .or()
                        .eq(PlayerStats::getPlayerName, shortName)
                        .or()
                        .like(PlayerStats::getPlayerName, shortName))
                .last("LIMIT 1"));
    }

    private TeamStats selectTeamStats(String leagueId, String teamName) {
        if (teamName == null || teamName.isBlank()) return null;
        return teamStatsMapper.selectOne(new LambdaQueryWrapper<TeamStats>()
                .eq(TeamStats::getLeagueId, leagueId)
                .and(w -> w.eq(TeamStats::getTeamName, teamName)
                        .or()
                        .like(TeamStats::getTeamName, teamName)
                        .or()
                        .apply("{0} like concat('%', team_name, '%')", teamName))
                .last("LIMIT 1"));
    }

    private List<BattlePlayer> playersByBattle(String battleId) {
        return battlePlayerMapper.selectList(new LambdaQueryWrapper<BattlePlayer>()
                .eq(BattlePlayer::getBattleId, battleId));
    }

    private static String shortPlayerName(String name) {
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private static long playerDamage(RadarSample s) {
        return safeLong(firstNonNull(s.player().getHurtToHero(), s.player().getHurtToHeroTotal(), s.player().getHurtTotal()));
    }

    private static long playerBeHurt(RadarSample s) {
        return safeLong(firstNonNull(s.player().getBeHurtTotal(), s.player().getBeHurtByHeroTotal()));
    }

    private static int killParticipation(RadarSample s) {
        return safeInt(s.player().getKillNum()) + safeInt(s.player().getAssistNum());
    }

    private static double durationMinutes(Long duration) {
        if (duration == null || duration <= 0) return 0;
        return duration > 10000 ? duration / 60000.0 : duration / 60.0;
    }

    private static double ratio(double a, double b) {
        return b == 0 ? 0 : a / b;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private record RadarSample(Match match, Battle battle, BattlePlayer player, TeamTotals teamTotals, double minutes) {}
    private record TeamTotals(long damage, long beHurt, int kills) {}
    private record SamplePool(String type, String label, List<String> leagueIds, List<RadarSample> samples) {}
    private record MetricDefinition(String key, String name, String unit, String displayUnit,
                                    Function<RadarSample, Double> reader,
                                    Function<RadarSample, Double> displayReader,
                                    String description) {}
}
