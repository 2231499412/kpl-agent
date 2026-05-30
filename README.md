# KPL Data Agent

面向 KPL 电竞公开数据的 Spring Boot 3 后端 MVP。当前版本聚焦“数据同步 + 增量刷新 + 结构化查询 + Agent 路由 + LLM 报告生成 + 查询缓存 + 同步任务追踪”的可用闭环。

## 项目结构

```text
src/main/java/com/kpl/agent
├── KplAgentApplication.java          # Spring Boot 启动类，开启 Mapper 扫描和定时能力
├── api
│   └── KplApiClient.java             # KPL 官方公开接口客户端
├── ai
│   └── ReportGenerator.java          # OpenAI 兼容 LLM 报告生成，未配置 Key 时自动降级
├── config
│   ├── AiConfig.java                 # AI API 配置
│   ├── MybatisPlusConfig.java        # createdAt / updatedAt 自动填充
│   ├── OpenApiConfig.java            # Swagger / OpenAPI 文档配置
│   ├── RedisConfig.java              # RedisTemplate JSON 序列化配置
│   └── RestTemplateConfig.java       # 外部 HTTP 客户端配置
├── controller
│   ├── ApiResponse.java              # REST 统一响应体
│   ├── AgentController.java          # 自然语言问答入口
│   ├── GlobalExceptionHandler.java   # 统一异常处理
│   ├── LeagueController.java         # 赛事列表和最新赛季查询
│   ├── QueryController.java          # 直接查询入口，方便调试 Tool
│   └── SyncController.java           # 手动触发数据同步
├── entity                            # MySQL 表实体
├── mapper                            # MyBatis-Plus Mapper
├── service
│   ├── AgentIntent.java              # Agent 结构化意图对象
│   ├── AgentIntentRecognizer.java    # LLM JSON 意图识别，失败时规则兜底
│   ├── DataSyncService.java          # 赛事、赛程、对局、榜单同步
│   ├── KplAgentService.java          # 意图识别和工具路由
│   ├── KplSyncScheduler.java         # 可选定时同步任务
│   ├── LeagueQueryService.java       # 最新赛季和赛事查询
│   └── QueryCacheService.java        # Redis 查询缓存封装
└── tool
    ├── HeroStatsTool.java            # 英雄榜查询
    ├── MatchAnalysisTool.java        # 比赛复盘查询
    ├── PlayerStatsTool.java          # 选手榜查询
    └── TeamStatsTool.java            # 战队榜查询
```

```text
frontend
├── src/App.vue                       # 电竞数据中控台主界面
├── src/styles.css                    # 视觉系统和布局样式
└── vite.config.js                    # Vite 代理到后端 /api
```

## 启动

1. 创建 MySQL 数据库并执行 `src/main/resources/schema.sql`。
2. 按需修改 `src/main/resources/application.yml` 里的 MySQL、Redis、KPL API 和 AI 配置。
3. 可选配置环境变量：

```powershell
$env:AI_API_KEY="你的 OpenAI 兼容 API Key"
$env:AI_BASE_URL="https://你的服务/v1"
$env:AI_MODEL="你的模型名"
```

4. 如需开启定时同步，在 `application.yml` 中设置：

```yaml
kpl:
  sync:
    enabled: true
    latest-cron: "0 0/30 * * * ?"
```

5. 编译并启动：

```powershell
mvn clean package
mvn spring-boot:run
```

默认端口是 `9090`。

Swagger UI 地址：

```text
http://localhost:9090/swagger-ui.html
```

应用、MySQL、Redis 可以通过 Docker Compose 启动：

```powershell
Copy-Item .env.example .env
mvn -q -DskipTests package
docker compose up -d
```

Compose 默认将 MySQL 暴露到宿主机 `13307`，应用暴露到宿主机 `9091`，避免和本机已有服务冲突。

## 常用接口

```http
POST /api/sync/leagues
POST /api/sync/latest
POST /api/sync/latest/incremental
POST /api/sync/latest/deep-incremental?matchLimit=10
POST /api/sync/season?leagueId=20260001&deep=false
GET  /api/sync/jobs?limit=10
GET  /api/sync/jobs/{id}
POST /api/sync/jobs/{id}/retry
GET  /api/sync/cursors

GET  /api/leagues?limit=20
GET  /api/leagues/latest

GET  /api/query/player?name=一诺
GET  /api/query/hero?name=公孙离
GET  /api/query/hero/top?sort=pick
GET  /api/query/team?name=AG
GET  /api/query/team/ranking
GET  /api/query/match/recent?team=AG
GET  /api/system/status
GET  /actuator/health

POST /api/agent/chat
Content-Type: application/json

{"message": "AG 最近比赛复盘一下"}
```

除 `leagueId` 明确传入外，查询接口会默认使用数据库中的最新 KPL 赛事。REST 响应统一为：

```json
{"success": true, "message": "ok", "data": {}}
```

## 后续设计建议

- Agent 层已支持“LLM JSON 意图识别 + 本地规则兜底”，下一步可以升级为真正的 tool/function calling，把工具 schema 暴露给模型。
- 数据同步已经记录 `sync_job` 和 `sync_cursor`，支持失败任务重试、最新赛季增量刷新和可选后台定时同步。下一步可以继续细化到“只深度同步已结束但未入库详情的比赛”。
- Redis 已用于选手、英雄、战队和近期比赛查询缓存，key 按 `kpl:{leagueId}:...` 组织，并在赛季同步后清理该赛季缓存。
- Milvus/RAG 先只放英雄技能说明、版本公告、赛制规则；结构化赛事数据仍然走 MySQL，不要向量化一切。

更多运行说明见 [docs/operations.md](docs/operations.md)。

## 前端页面

开发环境启动：

```powershell
cd frontend
npm install
npm run dev -- --port 15173
```

访问：

```text
http://localhost:15173
```

页面会通过 Vite 代理访问后端 `/api`，需要先确保后端在 `http://localhost:9090` 运行。
