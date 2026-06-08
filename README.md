# KPL Data Agent — 王者荣耀职业联赛数据分析平台

> 基于 Spring Boot 3 + Vue 3 的全栈电竞数据分析系统，集成 AI 智能复盘、BP 模拟、英雄梯度分析等功能。

<!-- 在此处插入主页截图 -->
<!-- ![主页](screenshots/home.png) -->

## 项目简介

KPL Data Agent 是一个面向王者荣耀职业联赛（KPL）的数据分析平台，自动从腾讯官方 API 同步赛事数据，提供战队/选手/英雄多维度数据查询，并集成大语言模型实现自然语言交互的 AI 复盘功能。

### 核心亮点

- **全量数据同步**：自动从腾讯 KPL 官方 API 抓取赛事、赛程、对局、选手、英雄、装备数据，支持增量刷新与任务追踪
- **AI 智能复盘**：接入大语言模型（MiMo），支持自然语言提问，自动查询相关数据并生成分析报告，流式输出（SSE）
- **BP 模拟与分析**：自由模拟 Ban/Pick 博弈过程，基于历史数据的 BP 策略分析
- **对位雷达图**：八维数据可视化对比，支持按分路切换，胜负方高亮
- **英雄梯度榜**：基于 BP 率、禁用率、胜率的加权评分算法，百分位自动分档
- **装备梯度榜**：按分路筛选，装备出场率/胜率可视化
- **深色/亮色主题**：全局主题切换，所有页面适配

## 功能模块

<!-- 在此处插入各功能截图 -->

### 1. 主页（HomePage）

横向滚动五屏展示：品牌首屏、数据概览、核心功能、赛事工具、BP 模拟引导。数据概览实时显示战队数、比赛数、英雄池统计。底部上滑展示页脚（作者信息、开源项目、技术栈、免责声明）。

<!-- ![主页](screenshots/home.png) -->

### 2. 数据排名（Rankings）

支持战队、选手、英雄三维度排名查询。左侧栏选择赛事，右侧展示排行榜。底部版本动态区域自动拉取腾讯官方公告。

<!-- ![数据排名](screenshots/rankings.png) -->

### 3. 赛程查询（Matches）

按赛事查看赛程列表，支持比赛详情展开（对局结果、MVP、英雄选择）。

<!-- ![赛程查询](screenshots/matches.png) -->

### 4. 装备分析（Equipment）

装备出场率与胜率分析，按分路（对抗路/打野/中路/发育路/游走）筛选，梯度分档展示，支持排序动画。

<!-- ![装备分析](screenshots/equipment.png) -->

### 5. 英雄梯度榜（TierList）

基于公式的加权评分：`评分 = BP率 x 50 + 禁用率 x 30 + 胜率 x 20`，按百分位自动分为 T0 ~ T4 五档。分路筛选时带 FLIP 排序动画。

<!-- ![英雄梯度](screenshots/tier-list.png) -->

### 6. 英雄图鉴（HeroGallery）

全英雄卡片展示，按分路筛选，点击查看详情。

<!-- ![英雄图鉴](screenshots/hero-gallery.png) -->

### 7. 对位雷达（LaneRadar）

八维数据雷达图对比（击杀、死亡、助攻、经济、伤害、承伤、参团率、视野），支持按比赛/小局/分路切换，胜负方颜色区分，MVP 标记。

<!-- ![对位雷达](screenshots/lane-radar.png) -->

### 8. BP 模拟器（BpSimulator）

自由模拟 Ban/Pick 过程，支持双阵营交替选择，英雄池按分路筛选。

<!-- ![BP模拟器](screenshots/bp-simulator.png) -->

### 9. BP 分析（BpAnalysis）

基于历史数据的 Ban/Pick 策略分析，展示热门英雄选取/禁用趋势。

<!-- ![BP分析](screenshots/bp-analysis.png) -->

### 10. AI 复盘（Agent）

自然语言问答入口，支持流式输出。示例问题：
- "AG 最近比赛复盘一下"
- "公孙离胜率多少"
- "一诺数据怎么样"
- "积分榜"

<!-- ![AI复盘](screenshots/agent.png) -->

## 技术架构

<!-- 在此处插入架构图 -->

```
┌─────────────────────────────────────────────────────────┐
│                    Vue 3 前端                            │
│  Element Plus · Vue Router · SSE · ECharts              │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP / SSE (Vite Proxy)
┌──────────────────────▼──────────────────────────────────┐
│              Spring Boot 3 后端                          │
│  Controller → Service → Mapper (MyBatis-Plus)           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │ 数据同步  │ │ 查询缓存  │ │ AI Agent │ │ 定时任务   │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └───────────┘  │
│       │            │            │                        │
│  ┌────▼─────┐ ┌────▼─────┐ ┌───▼──────┐                │
│  │ 腾讯 API │ │  Redis   │ │ MiMo LLM │                │
│  └──────────┘ └──────────┘ └──────────┘                │
│       │                                                  │
│  ┌────▼─────┐                                           │
│  │  MySQL   │                                           │
│  └──────────┘                                           │
└─────────────────────────────────────────────────────────┘
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5、Spring MVC |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7 |
| AI 接口 | OpenAI 兼容协议（MiMo 大模型） |
| 前端框架 | Vue 3、Vue Router |
| UI 组件 | Element Plus |
| 图表 | ECharts |
| 构建工具 | Maven、Vite |
| 容器化 | Docker Compose |

## 数据库设计

共 10 张核心表：

| 表名 | 说明 | 数据量 |
|------|------|--------|
| `league` | 赛事表 | 31 |
| `match` | 比赛表 | 2,711 |
| `battle` | 对局表 | 10,420 |
| `team_stats` | 战队赛季统计 | 1,749 |
| `player_stats` | 选手赛季统计 | 10,098 |
| `hero_stats` | 英雄赛季统计 | 8,306 |
| `battle_player_stats` | 对局选手数据 | 626,830 |
| `battle_player_equipment` | 对局选手装备 | 3,654,448 |
| `equipment` | 装备基础信息 | 230 |
| `sync_job` / `sync_cursor` | 同步任务追踪 | — |

<!-- 在此处插入 ER 图 -->
<!-- ![ER图](screenshots/er-diagram.png) -->

## 项目结构

```text
src/main/java/com/kpl/agent
├── KplAgentApplication.java          # 启动类
├── api/
│   ├── KplApiClient.java             # 腾讯 KPL 官方 API 客户端
│   └── TencentNewsClient.java        # 腾讯新闻公告客户端
├── config/
│   ├── WebConfig.java                # CORS 跨域配置
│   ├── RedisConfig.java              # Redis 序列化配置
│   ├── MybatisPlusConfig.java        # 自动填充配置
│   └── AiConfig.java                 # AI 模型配置
├── controller/
│   ├── AgentController.java          # AI 对话（同步 + SSE 流式）
│   ├── QueryController.java          # 数据查询入口
│   ├── LeagueController.java         # 赛事查询 + 全局统计
│   └── SyncController.java           # 数据同步触发
├── entity/                           # MyBatis-Plus 实体
├── mapper/                           # Mapper 接口
├── service/
│   ├── KplAgentService.java          # AI Agent 核心（意图识别 + 工具路由）
│   ├── DataSyncService.java          # 数据同步逻辑
│   ├── QueryCacheService.java        # Redis 缓存封装
│   └── LeagueQueryService.java       # 赛事查询
└── tool/
    ├── TeamStatsTool.java            # 战队数据查询工具
    ├── PlayerStatsTool.java          # 选手数据查询工具
    ├── HeroStatsTool.java            # 英雄数据查询工具
    └── MatchAnalysisTool.java        # 比赛复盘工具

frontend/src/
├── App.vue                           # 全局布局、背景音乐、主题切换
├── router/index.js                   # 路由配置
├── views/
│   ├── HomePage.vue                  # 主页（横向滚动五屏 + 页脚）
│   ├── RankingsPage.vue              # 数据排名
│   ├── MatchesPage.vue               # 赛程查询
│   ├── EquipmentPage.vue             # 装备分析
│   ├── TierListPage.vue              # 英雄梯度榜
│   ├── HeroGalleryPage.vue           # 英雄图鉴
│   ├── LaneRadarPage.vue             # 对位雷达图
│   ├── BpSimulatorPage.vue           # BP 模拟器
│   ├── BpAnalysisPage.vue            # BP 分析
│   └── AgentPage.vue                 # AI 复盘对话
├── components/
│   ├── SideBar.vue                   # 侧边导航栏（PC）/ 底部导航栏（移动端）
│   └── LoadingScreen.vue             # 加载动画
└── utils/
    └── theme.js                      # 主题切换工具
```

## 快速启动

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 7+
- Node.js 18+

### 后端启动

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE kpl_agent DEFAULT CHARACTER SET utf8mb4;"

# 2. 配置环境变量（可选，也可直接修改 application.yml）
export AI_API_KEY="你的API Key"
export AI_BASE_URL="https://你的AI服务/v1"
export AI_MODEL="模型名"

# 3. 编译启动
mvn clean package -DskipTests
mvn spring-boot:run
```

后端默认运行在 `http://localhost:9090`

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:15173`（或 15174），通过 Vite 代理访问后端 API。

### Docker Compose 一键启动

```bash
cp .env.example .env
mvn -q -DskipTests package
docker compose up -d
```

## API 接口

### 数据查询

```http
GET /api/leagues?limit=20                    # 赛事列表
GET /api/leagues/latest                      # 最新赛事
GET /api/leagues/stats                       # 全局统计（战队/比赛/英雄数）

GET /api/query/team/ranking                  # 战队排名
GET /api/query/hero/top?sort=pick&limit=50   # 英雄出场率排行
GET /api/query/player?name=一诺              # 选手查询
GET /api/query/match/schedule                # 赛程查询
GET /api/query/radar/lane?...                # 对位雷达数据
GET /api/query/equipment/top?sort=pick       # 装备排行
GET /api/query/patch-notes                   # 版本动态
```

### AI 对话

```http
# 同步对话
POST /api/agent/chat
Content-Type: application/json
{"message": "AG 最近比赛复盘一下"}

# 流式对话（SSE）
POST /api/agent/chat/stream
Content-Type: application/json
{"message": "公孙离胜率多少"}
```

### 数据同步

```http
POST /api/sync/leagues                       # 同步赛事列表
POST /api/sync/latest                        # 同步最新赛季
POST /api/sync/latest/incremental            # 增量同步
GET  /api/sync/jobs?limit=10                 # 同步任务记录
```

## 竞赛适用性

本项目适用于 Spring Boot Web 应用项目实训大赛，具备以下特点：

1. **完整的全栈架构**：Spring Boot 3 后端 + Vue 3 前端，RESTful API 设计
2. **数据库设计合理**：10 张核心表，覆盖赛事/比赛/对局/选手/英雄/装备全维度
3. **ORM 框架应用**：MyBatis-Plus 简化 CRUD，自定义 SQL 处理复杂查询
4. **缓存优化**：Redis 缓存热点查询，支持 TTL 过期和主动清理
5. **AI 能力集成**：大语言模型接入，意图识别 + 工具路由 + 流式输出
6. **实际业务场景**：电竞数据分析，非管理系统模板，有真实数据支撑
7. **响应式设计**：PC 端 + 移动端适配，深色/亮色主题切换

## 许可证

MIT License
