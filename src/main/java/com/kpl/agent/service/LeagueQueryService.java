package com.kpl.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.League;
import com.kpl.agent.mapper.LeagueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 赛事查询服务：统一处理最新赛季、默认 leagueId 和赛事列表查询。
 */
@Service
@RequiredArgsConstructor
public class LeagueQueryService {

    private final LeagueMapper leagueMapper;

    public List<League> listLeagues(int limit) {
        return leagueMapper.selectList(
                new LambdaQueryWrapper<League>()
                        .orderByDesc(League::getStartTime)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    public League latestKplLeague() {
        return leagueMapper.selectOne(
                new LambdaQueryWrapper<League>()
                        .likeRight(League::getLeagueType, "kpl")
                        .orderByDesc(League::getStartTime)
                        .last("LIMIT 1"));
    }

    public String requireLeagueId(String leagueId) {
        if (leagueId != null && !leagueId.isBlank()) {
            return leagueId;
        }
        League latest = latestKplLeague();
        if (latest == null) {
            throw new IllegalArgumentException("暂无赛事数据，请先调用 /api/sync/leagues 或 /api/sync/latest 同步数据");
        }
        return latest.getLeagueId();
    }
}
