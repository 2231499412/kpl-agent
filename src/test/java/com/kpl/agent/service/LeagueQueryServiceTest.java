package com.kpl.agent.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.kpl.agent.entity.League;
import com.kpl.agent.mapper.LeagueMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeagueQueryServiceTest {

    private final LeagueMapper leagueMapper = mock(LeagueMapper.class);
    private final LeagueQueryService leagueQueryService = new LeagueQueryService(leagueMapper);

    @Test
    void requireLeagueIdReturnsProvidedValue() {
        assertThat(leagueQueryService.requireLeagueId("20260001")).isEqualTo("20260001");
    }

    @Test
    void requireLeagueIdFallsBackToLatestLeague() {
        League league = new League();
        league.setLeagueId("20260002");
        when(leagueMapper.selectOne(any(Wrapper.class))).thenReturn(league);

        assertThat(leagueQueryService.requireLeagueId(null)).isEqualTo("20260002");
    }

    @Test
    void requireLeagueIdThrowsWhenNoLeagueExists() {
        when(leagueMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> leagueQueryService.requireLeagueId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂无赛事数据");
    }
}
