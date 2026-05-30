# 运维说明

## 本地依赖

复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

先构建应用 jar：

```powershell
mvn -q -DskipTests package
```

启动应用、MySQL 和 Redis：

```powershell
docker compose up -d
```

应用启动后，Flyway 会自动执行 `src/main/resources/db/migration` 下的数据库迁移。
Compose 默认把 MySQL 映射到宿主机 `13307`，避免和本机已有 MySQL 的 `3306/3307` 冲突；应用容器内部仍连接 `mysql:3306`。
Compose 默认把应用映射到宿主机 `9091`，避免和本机直跑 Spring Boot 的 `9090` 冲突。

只启动依赖服务、不启动应用容器：

```powershell
docker compose up -d mysql redis
```

## 本机启动应用

```powershell
$env:AI_API_KEY=""
mvn spring-boot:run
```

访问：

```text
http://localhost:9091/swagger-ui.html
http://localhost:9091/api/system/status
http://localhost:9091/actuator/health
```

## 推荐同步流程

第一次使用：

```http
POST /api/sync/leagues
POST /api/sync/latest
```

日常轻量刷新：

```http
POST /api/sync/latest/incremental
```

补齐比赛复盘详情：

```http
POST /api/sync/latest/deep-incremental?matchLimit=10
```

查看同步任务和游标：

```http
GET /api/sync/jobs?limit=20
GET /api/sync/cursors
```

## 定时同步

`application.yml` 默认关闭定时同步。开启后：

- `kpl.sync.latest-cron`：轻量增量刷新，默认每 30 分钟。
- `kpl.sync.deep-cron`：深度补详情，默认每天 03:10。

```yaml
kpl:
  sync:
    enabled: true
```

## 数据库升级

新项目直接依赖 Flyway 自动建表。旧项目如果已经手动执行过 `schema.sql`，`baseline-on-migrate=true` 会让 Flyway 从当前库状态开始管理后续迁移。

## 前端联调

默认允许以下本地前端来源跨域访问 `/api/**`：

```text
http://localhost:5173
http://localhost:5174
http://localhost:15173
http://localhost:3000
```

可通过环境变量覆盖：

```powershell
$env:KPL_CORS_ALLOWED_ORIGINS="http://localhost:5173,http://localhost:8080"
```

启动前端：

```powershell
cd frontend
npm install
npm run dev -- --port 15173
```

访问：

```text
http://localhost:15173
```
