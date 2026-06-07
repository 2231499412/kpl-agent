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
import java.util.Comparator;
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
        if (match == null) {
            return Map.of("error", "比赛不存在: " + matchId);
        }
        if ("all".equalsIgnoreCase(String.valueOf(battleId)) || "summary".equalsIgnoreCase(String.valueOf(battleId))) {
            return buildMatchRadar(leagueId, match, resolvedRole, position);
        }
        Battle battle = selectBattle(battleId);
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
        result.put("highlights", buildHighlights(samplesFromBattle(match, battle, currentPlayers), position));
        return result;
    }

    private Map<String, Object> buildMatchRadar(String leagueId, Match match, String resolvedRole, int position) {
        String resolvedLeagueId = leagueId != null && !leagueId.isBlank() ? leagueId : match.getLeagueId();
        League league = selectLeague(resolvedLeagueId);
        List<Battle> matchBattles = battleMapper.selectList(new LambdaQueryWrapper<Battle>()
                .eq(Battle::getMatchId, match.getMatchId())
                .orderByAsc(Battle::getBattleSeq));
        if (matchBattles.isEmpty()) {
            return Map.of("error", "该比赛暂无小局数据");
        }

        List<String> battleIds = matchBattles.stream().map(Battle::getBattleId).filter(Objects::nonNull).toList();
        List<BattlePlayer> players = battlePlayerMapper.selectList(new LambdaQueryWrapper<BattlePlayer>()
                .in(BattlePlayer::getBattleId, battleIds));
        Map<String, List<BattlePlayer>> playersByBattle = players.stream()
                .collect(Collectors.groupingBy(BattlePlayer::getBattleId));

        Map<Integer, AggregateSide> sides = new LinkedHashMap<>();
        Map<Integer, TeamTotalsBuilder> totals = new LinkedHashMap<>();
        double totalMinutes = 0;
        for (Battle battle : matchBattles) {
            List<BattlePlayer> battlePlayers = playersByBattle.getOrDefault(battle.getBattleId(), List.of());
            if (battlePlayers.isEmpty()) continue;
            double minutes = durationMinutes(battle.getGameDuration());
            if (minutes <= 0) continue;
            totalMinutes += minutes;

            Map<Integer, TeamTotals> battleTotals = battlePlayers.stream()
                    .filter(p -> p.getCamp() != null)
                    .collect(Collectors.groupingBy(BattlePlayer::getCamp,
                            Collectors.collectingAndThen(Collectors.toList(), this::teamTotals)));
            battleTotals.forEach((camp, total) -> totals.computeIfAbsent(camp, k -> new TeamTotalsBuilder()).add(total));

            for (BattlePlayer player : battlePlayers) {
                if (!Objects.equals(player.getPosition(), position) || player.getCamp() == null) {
                    continue;
                }
                sides.computeIfAbsent(player.getCamp(), k -> new AggregateSide()).add(player);
            }
        }

        RadarSample blue = aggregateSample(match, matchBattles.get(0), sides.get(1), totals.get(1), totalMinutes);
        RadarSample red = aggregateSample(match, matchBattles.get(0), sides.get(2), totals.get(2), totalMinutes);
        if (blue == null || red == null) {
            return Map.of("error", "该比赛缺少全场双方同分路数据");
        }

        Map<String, List<BattlePlayerEquip>> equipMap = battlePlayerEquipMapper.selectList(
                        new LambdaQueryWrapper<BattlePlayerEquip>().in(BattlePlayerEquip::getBattleId, battleIds))
                .stream()
                .collect(Collectors.groupingBy(BattlePlayerEquip::getPlayerName));

        SamplePool pool = selectSummarySamplePool(resolvedLeagueId, position);
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
        basis.put("summaryBattleCount", matchBattles.size());

        Map<String, Object> battleSummary = new LinkedHashMap<>();
        battleSummary.put("battleId", "all");
        battleSummary.put("matchId", match.getMatchId());
        battleSummary.put("title", "全场总结");
        battleSummary.put("winCamp", matchWinnerCamp(match));
        battleSummary.put("gameDuration", Math.round(totalMinutes * 60000));
        battleSummary.put("summary", true);
        battleSummary.put("battleCount", matchBattles.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "lane_radar_summary");
        result.put("league", league);
        result.put("match", match);
        result.put("battle", battleSummary);
        result.put("role", resolvedRole);
        result.put("basis", basis);
        result.put("indicators", indicators);
        Map<String, Object> blueSide = buildSide(blue, definitions, pool, resolvedLeagueId, equipMap);
        Map<String, Object> redSide = buildSide(red, definitions, pool, resolvedLeagueId, equipMap);
        blueSide.put("heroGames", buildHeroGames(1, position, matchBattles, playersByBattle));
        redSide.put("heroGames", buildHeroGames(2, position, matchBattles, playersByBattle));
        result.put("blue", blueSide);
        result.put("red", redSide);
        result.put("highlights", buildHighlights(summarySamplesFromMatch(match, matchBattles, playersByBattle, null), position));
        return result;
    }

    private RadarSample aggregateSample(Match match, Battle battle, AggregateSide side, TeamTotalsBuilder totals, double minutes) {
        if (side == null || totals == null || minutes <= 0) return null;
        return new RadarSample(match, battle, side.toPlayer(), totals.toTotals(), minutes);
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

    private List<Map<String, Object>> buildHeroGames(int camp, int position, List<Battle> battles,
                                                      Map<String, List<BattlePlayer>> playersByBattle) {
        List<Map<String, Object>> games = new ArrayList<>();
        for (Battle battle : battles) {
            BattlePlayer player = playersByBattle.getOrDefault(battle.getBattleId(), List.of()).stream()
                    .filter(p -> Objects.equals(p.getCamp(), camp) && Objects.equals(p.getPosition(), position))
                    .findFirst()
                    .orElse(null);
            if (player == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("battleId", battle.getBattleId());
            row.put("battleSeq", battle.getBattleSeq());
            row.put("winCamp", battle.getWinCamp());
            row.put("result", Objects.equals(battle.getWinCamp(), camp) ? "胜" : "败");
            row.put("heroId", player.getHeroId());
            row.put("heroName", player.getHeroName());
            row.put("summonerAbilityId", player.getSummonerAbilityId());
            row.put("summonerAbilityName", player.getSummonerAbilityName());
            row.put("summonerAbilityIcon", summonerAbilityIcon(player.getSummonerAbilityId()));
            row.put("kills", safeInt(player.getKillNum()));
            row.put("deaths", safeInt(player.getDeathNum()));
            row.put("assists", safeInt(player.getAssistNum()));
            row.put("gold", safeInt(player.getGold()));
            row.put("isMvp", safeInt(player.getIsMvp()));
            row.put("isLoseMvp", safeInt(player.getIsLoseMvp()));
            games.add(row);
        }
        return games;
    }

    private List<Map<String, Object>> buildHighlights(List<RadarSample> samples, int currentPosition) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        List<HighlightDefinition> definitions = List.of(
                new HighlightDefinition("goldPerMinute", "分均经济最高", "", s -> safeInt(s.player().getGold()) / s.minutes()),
                new HighlightDefinition("damagePerMinute", "分均伤害最高", "", s -> playerDamage(s) / s.minutes()),
                new HighlightDefinition("participationRate", "参团率最高", "%", s -> ratio(killParticipation(s), s.teamTotals().kills())),
                new HighlightDefinition("kda", "KDA最高", "", s -> killParticipation(s) / Math.max(safeInt(s.player().getDeathNum()), 1.0))
        );
        return definitions.stream()
                .map(def -> samples.stream()
                        .filter(s -> s != null && s.minutes() > 0)
                        .map(s -> new HighlightCandidate(s, def.reader().apply(s)))
                        .filter(c -> Double.isFinite(c.value()))
                        .max(Comparator.comparingDouble(HighlightCandidate::value))
                        .filter(c -> Objects.equals(c.sample().player().getPosition(), currentPosition))
                        .map(c -> buildHighlight(def, c.sample(), c.value()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> buildHighlight(HighlightDefinition def, RadarSample sample, double value) {
        BattlePlayer player = sample.player();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", def.key());
        row.put("label", def.label());
        row.put("playerName", player.getPlayerName());
        row.put("teamName", player.getTeamName());
        row.put("camp", player.getCamp());
        row.put("position", player.getPosition());
        row.put("positionDesc", player.getPositionDesc());
        row.put("heroId", player.getHeroId());
        row.put("heroName", player.getHeroName());
        row.put("value", round(value, 2));
        row.put("displayValue", "%".equals(def.unit()) ? round(value * 100, 1) : round(value, 1));
        row.put("unit", def.unit());
        return row;
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

    private SamplePool selectSummarySamplePool(String leagueId, int position) {
        List<RadarSample> leagueSamples = buildMatchSummarySamples(List.of(leagueId), position);
        if (leagueSamples.size() >= MIN_SAMPLE_SIZE) {
            return new SamplePool("league_summary", "当前赛事同分路全场汇总百分位", List.of(leagueId), leagueSamples);
        }

        List<String> recentLeagueIds = recentLeagueIds(leagueId);
        List<RadarSample> recentSamples = buildMatchSummarySamples(recentLeagueIds, position);
        if (recentSamples.size() >= MIN_SAMPLE_SIZE) {
            return new SamplePool("recent2_summary", "近两个赛事同分路全场汇总百分位", recentLeagueIds, recentSamples);
        }

        List<RadarSample> historySamples = buildMatchSummarySamples(List.of(), position);
        return new SamplePool("history_summary", "历史全量同分路全场汇总百分位", List.of(), historySamples);
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

    private List<RadarSample> buildMatchSummarySamples(List<String> leagueIds, int position) {
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
                .in(Battle::getMatchId, matchIds)
                .orderByAsc(Battle::getBattleSeq));
        if (battles.isEmpty()) {
            return List.of();
        }
        List<String> battleIds = battles.stream().map(Battle::getBattleId).filter(Objects::nonNull).toList();
        List<BattlePlayer> players = battlePlayerMapper.selectList(new LambdaQueryWrapper<BattlePlayer>()
                .in(BattlePlayer::getBattleId, battleIds));
        Map<String, List<Battle>> battlesByMatch = battles.stream()
                .filter(b -> b.getMatchId() != null)
                .collect(Collectors.groupingBy(Battle::getMatchId));
        Map<String, List<BattlePlayer>> playersByBattle = players.stream()
                .collect(Collectors.groupingBy(BattlePlayer::getBattleId));

        List<RadarSample> samples = new ArrayList<>();
        for (Map.Entry<String, List<Battle>> entry : battlesByMatch.entrySet()) {
            Match match = matchMap.get(entry.getKey());
            if (match == null) continue;
            samples.addAll(summarySamplesFromMatch(match, entry.getValue(), playersByBattle, position));
        }
        return samples;
    }

    private List<RadarSample> summarySamplesFromMatch(Match match, List<Battle> battles,
                                                       Map<String, List<BattlePlayer>> playersByBattle, int position) {
        return summarySamplesFromMatch(match, battles, playersByBattle, Integer.valueOf(position));
    }

    private List<RadarSample> summarySamplesFromMatch(Match match, List<Battle> battles,
                                                       Map<String, List<BattlePlayer>> playersByBattle, Integer position) {
        if (battles == null || battles.isEmpty()) {
            return List.of();
        }
        Map<String, AggregateSide> sides = new LinkedHashMap<>();
        Map<Integer, TeamTotalsBuilder> totals = new LinkedHashMap<>();
        double totalMinutes = 0;

        for (Battle battle : battles) {
            List<BattlePlayer> battlePlayers = playersByBattle.getOrDefault(battle.getBattleId(), List.of());
            if (battlePlayers.isEmpty()) continue;
            double minutes = durationMinutes(battle.getGameDuration());
            if (minutes <= 0) continue;
            totalMinutes += minutes;

            Map<Integer, TeamTotals> battleTotals = battlePlayers.stream()
                    .filter(p -> p.getCamp() != null)
                    .collect(Collectors.groupingBy(BattlePlayer::getCamp,
                            Collectors.collectingAndThen(Collectors.toList(), this::teamTotals)));
            battleTotals.forEach((camp, total) -> totals.computeIfAbsent(camp, k -> new TeamTotalsBuilder()).add(total));

            for (BattlePlayer player : battlePlayers) {
                if ((position != null && !Objects.equals(player.getPosition(), position)) || player.getCamp() == null) {
                    continue;
                }
                String key = player.getCamp() + "::" + (player.getPlayerName() == null ? "" : player.getPlayerName());
                sides.computeIfAbsent(key, k -> new AggregateSide()).add(player);
            }
        }

        if (totalMinutes <= 0) {
            return List.of();
        }
        List<RadarSample> samples = new ArrayList<>();
        Battle firstBattle = battles.get(0);
        for (AggregateSide side : sides.values()) {
            BattlePlayer player = side.toPlayer();
            if (player == null) continue;
            TeamTotalsBuilder teamTotals = totals.get(player.getCamp());
            RadarSample sample = aggregateSample(match, firstBattle, side, teamTotals, totalMinutes);
            if (sample != null) {
                samples.add(sample);
            }
        }
        return samples;
    }

    private List<RadarSample> samplesFromBattle(Match match, Battle battle, List<BattlePlayer> players, int position) {
        return samplesFromBattle(match, battle, players, Integer.valueOf(position));
    }

    private List<RadarSample> samplesFromBattle(Match match, Battle battle, List<BattlePlayer> players) {
        return samplesFromBattle(match, battle, players, null);
    }

    private List<RadarSample> samplesFromBattle(Match match, Battle battle, List<BattlePlayer> players, Integer position) {
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
            if (position != null && !Objects.equals(player.getPosition(), position)) {
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

    private static int matchWinnerCamp(Match match) {
        if (match == null) return 0;
        if (match.getWinCamp() != null && match.getWinCamp() > 0) return match.getWinCamp();
        int blueScore = safeInt(match.getCamp1Score());
        int redScore = safeInt(match.getCamp2Score());
        if (blueScore == redScore) return 0;
        return blueScore > redScore ? 1 : 2;
    }

    private static class TeamTotalsBuilder {
        private long damage;
        private long beHurt;
        private int kills;

        void add(TeamTotals total) {
            if (total == null) return;
            damage += total.damage();
            beHurt += total.beHurt();
            kills += total.kills();
        }

        TeamTotals toTotals() {
            return new TeamTotals(damage, beHurt, kills);
        }
    }

    private static class AggregateSide {
        private BattlePlayer representative;
        private int kills;
        private int deaths;
        private int assists;
        private int gold;
        private long hurtTotal;
        private long hurtToHero;
        private long beHurtTotal;
        private long hurtToHeroTotal;
        private long beHurtByHeroTotal;

        void add(BattlePlayer player) {
            if (player == null) return;
            if (representative == null || safeInt(player.getIsMvp()) > safeInt(representative.getIsMvp())) {
                representative = player;
            }
            kills += safeInt(player.getKillNum());
            deaths += safeInt(player.getDeathNum());
            assists += safeInt(player.getAssistNum());
            gold += safeInt(player.getGold());
            hurtTotal += safeLong(player.getHurtTotal());
            hurtToHero += safeLong(player.getHurtToHero());
            beHurtTotal += safeLong(player.getBeHurtTotal());
            hurtToHeroTotal += safeLong(player.getHurtToHeroTotal());
            beHurtByHeroTotal += safeLong(player.getBeHurtByHeroTotal());
        }

        BattlePlayer toPlayer() {
            if (representative == null) return null;
            BattlePlayer player = new BattlePlayer();
            player.setBattleId("all");
            player.setTeamId(representative.getTeamId());
            player.setTeamName(representative.getTeamName());
            player.setPlayerName(representative.getPlayerName());
            player.setHeroId(representative.getHeroId());
            player.setHeroName(representative.getHeroName());
            player.setCamp(representative.getCamp());
            player.setPosition(representative.getPosition());
            player.setPositionDesc(representative.getPositionDesc());
            player.setSummonerAbilityId(representative.getSummonerAbilityId());
            player.setSummonerAbilityName(representative.getSummonerAbilityName());
            player.setSymbolIds(representative.getSymbolIds());
            player.setIsMvp(representative.getIsMvp());
            player.setIsLoseMvp(representative.getIsLoseMvp());
            player.setKillNum(kills);
            player.setDeathNum(deaths);
            player.setAssistNum(assists);
            player.setGold(gold);
            player.setHurtTotal(hurtTotal);
            player.setHurtToHero(hurtToHero);
            player.setBeHurtTotal(beHurtTotal);
            player.setHurtToHeroTotal(hurtToHeroTotal);
            player.setBeHurtByHeroTotal(beHurtByHeroTotal);
            return player;
        }
    }

    private record RadarSample(Match match, Battle battle, BattlePlayer player, TeamTotals teamTotals, double minutes) {}
    private record TeamTotals(long damage, long beHurt, int kills) {}
    private record SamplePool(String type, String label, List<String> leagueIds, List<RadarSample> samples) {}
    private record HighlightDefinition(String key, String label, String unit, Function<RadarSample, Double> reader) {}
    private record HighlightCandidate(RadarSample sample, double value) {}
    private record MetricDefinition(String key, String name, String unit, String displayUnit,
                                    Function<RadarSample, Double> reader,
                                    Function<RadarSample, Double> displayReader,
                                    String description) {}
}
