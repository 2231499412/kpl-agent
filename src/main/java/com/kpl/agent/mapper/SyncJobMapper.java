package com.kpl.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kpl.agent.entity.SyncJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步任务 Mapper：用于记录同步任务执行状态。
 */
@Mapper
public interface SyncJobMapper extends BaseMapper<SyncJob> {
}
