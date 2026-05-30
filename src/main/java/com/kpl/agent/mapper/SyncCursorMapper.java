package com.kpl.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kpl.agent.entity.SyncCursor;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步游标 Mapper：用于查询和更新增量同步位置。
 */
@Mapper
public interface SyncCursorMapper extends BaseMapper<SyncCursor> {
}
