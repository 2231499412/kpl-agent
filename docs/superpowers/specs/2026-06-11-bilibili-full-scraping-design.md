# B站视频BV号全量爬取设计方案

## 背景

当前数据库中只有16场比赛有B站视频BV号（覆盖87场小局），而总共有2711场比赛（10420场小局）。用户希望覆盖所有比赛的BV号，以便在英雄洞察页面点击推荐对局时能准确跳转到B站视频的对应小局。

## 目标

1. 爬取B站KPL官方账号（UID: 392836434）的所有比赛视频
2. 将视频与数据库中的比赛自动匹配
3. 获取每个视频的分P结构，正确映射每场小局的分P编号
4. 实现增量更新，支持断点续传

## 设计方案

### 1. 爬取B站账号视频列表

**挑战**：B站API有风控限制，无法直接调用空间视频列表API（返回-352错误）

**解决方案**：使用浏览器访问B站空间页面，提取视频列表

**实现方式**：
- 使用gstack browse工具或Playwright无头浏览器
- 访问 `https://space.bilibili.com/392836434/video`
- 模拟滚动加载，提取所有视频信息
- 按年份/赛季分批获取

**数据结构**：
```json
{
  "bvid": "BV1xxx",
  "title": "【2026KPL春季赛】5月23日 决赛 成都AG超玩会 vs 重庆狼队",
  "date": "2026-05-23",
  "team1": "成都AG超玩会",
  "team2": "重庆狼队"
}
```

### 2. 匹配数据库比赛

**匹配规则**：
1. **日期匹配**：视频发布日期与比赛日期相差≤3天
2. **队伍名称匹配**：
   - 完全包含：视频标题中的队伍名包含数据库中的队伍名
   - 去除城市前缀后匹配：如"成都AG超玩会" → "AG超玩会"

**匹配流程**：
```
视频标题 → 提取日期和队伍名称
          ↓
数据库查询 → 找到日期±3天内的比赛
          ↓
队伍匹配 → 验证两支队伍名称
          ↓
匹配成功 → 记录映射关系
```

### 3. 获取分P结构

**API调用**：
```
GET https://api.bilibili.com/x/web-interface/view?bvid={bvid}
```

**响应处理**：
```json
{
  "pages": [
    {"page": 1, "part": "第一局"},
    {"page": 2, "part": "第二局"},
    {"page": 3, "part": "第三局"}
  ]
}
```

**分P映射**：
- 提取包含"第X局"的分P
- 中文数字转换：一→1, 二→2, ..., 十→10
- 存储到数据库 `battle.page_num` 字段

### 4. 增量更新

**处理流程**：
1. 查询数据库中没有BV号的比赛
2. 按赛季/年份分批处理
3. 每批处理后记录进度
4. 支持断点续传

**进度记录**：
```sql
CREATE TABLE scrape_progress (
  id INT AUTO_INCREMENT PRIMARY KEY,
  league_id VARCHAR(20),
  last_processed_match_id VARCHAR(50),
  total_matches INT,
  processed_matches INT,
  matched_matches INT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 5. 错误处理

**错误类型**：
1. **API调用失败**：记录日志，跳过当前视频
2. **匹配失败**：记录到 `scrape_errors` 表，支持手动补充
3. **分P获取失败**：使用默认映射（battle_seq = page_num）

**请求频率控制**：
- 每次请求间隔0.5-1秒
- 每100次请求休息5秒
- 避免触发B站风控

### 6. 数据库表结构

**新增字段**：
```sql
-- battle表新增字段
ALTER TABLE battle ADD COLUMN page_num INT COMMENT 'B站视频实际分P编号' AFTER bvid;

-- 爬取进度表
CREATE TABLE scrape_progress (
  id INT AUTO_INCREMENT PRIMARY KEY,
  league_id VARCHAR(20),
  last_processed_match_id VARCHAR(50),
  total_matches INT,
  processed_matches INT,
  matched_matches INT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 爬取错误表
CREATE TABLE scrape_errors (
  id INT AUTO_INCREMENT PRIMARY KEY,
  match_id VARCHAR(50),
  error_type VARCHAR(50),
  error_message TEXT,
  video_bvid VARCHAR(20),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 实现步骤

### Phase 1: 基础框架
1. 创建Python爬取脚本框架
2. 实现B站视频列表获取（使用浏览器）
3. 实现视频标题解析（提取日期和队伍）

### Phase 2: 匹配逻辑
4. 实现数据库比赛查询
5. 实现日期和队伍名称匹配
6. 实现匹配结果验证

### Phase 3: 分P获取
7. 实现B站API调用
8. 实现分P结构解析
9. 实现page_num更新

### Phase 4: 增量更新
10. 实现进度记录
11. 实现断点续传
12. 实现错误处理

### Phase 5: 测试和优化
13. 测试单个赛季爬取
14. 测试全量爬取
15. 优化性能和错误处理

## 预期结果

- **覆盖率**：从0.6%提升到80%+
- **耗时**：全量爬取预计2-4小时
- **准确率**：自动匹配准确率约80%，剩余20%需要手动补充

## 风险和缓解

1. **B站风控**：控制请求频率，使用浏览器绕过API限制
2. **匹配错误**：记录错误日志，支持手动修正
3. **数据量大**：分批处理，支持断点续传

## 技术栈

- **Python 3.x**：主脚本语言
- **pymysql**：数据库连接
- **requests**：HTTP请求
- **re**：正则表达式解析
- **gstack browse / Playwright**：浏览器自动化
