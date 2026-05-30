package com.kpl.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kpl.agent.entity.HeroStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HeroStatsMapper extends BaseMapper<HeroStats> {
}
