# AGENTS.md

## Project

Spring Boot 3 + Vue 3 full-stack esports data analysis platform for KPL (King Pro League). Syncs match data from Tencent APIs, provides team/player/hero/equipment analytics, and an AI chat agent (MiMo LLM) for natural language queries.

## Commands

### Backend (Java 17, Maven)
```bash
mvn clean package -DskipTests          # Build jar
mvn spring-boot:run                     # Run dev server (port 9090)
mvn test                                # Run tests
```

### Frontend (Node 18+, Vite)
```bash
cd frontend
npm install
npm run dev                             # Dev server on port 15173
npm run build                           # Production build
```

### Docker (full stack)
```bash
cp .env.example .env                    # Configure env vars first
mvn -q -DskipTests package             # Must build jar before compose
docker compose up -d                    # Backend :9090, Frontend :9091
```

## Architecture

- **Backend**: Spring Boot 3.2.5 → MyBatis-Plus 3.5.6 → MySQL 8. Redis for query cache (600s TTL).
- **Frontend**: Vue 3 + Element Plus + ECharts, built with Vite. Vite dev proxy routes `/api` → `localhost:9090` with SSE-aware proxy config.
- **AI Agent**: Intent recognition (LLM-first, rule-based fallback) → tool routing (Player/Hero/Team/Match/Equip tools) → LLM report generation (streaming SSE).
- **Data sync**: Tencent KPL API → scheduled cron jobs (30min latest, 3am deep). Tracked via `sync_job`/`sync_cursor` tables.
- **Web search fallback**: DuckDuckGo HTML scraping (no API key needed). Kicks in when local data is empty or intent is UNKNOWN.
- **DotenvConfig**: `.env` file loaded at startup via `dotenv-java` (registered as `ApplicationContextInitializer`). Env vars override `.env` values.

## Key files

| Path | Purpose |
|------|---------|
| `src/main/java/com/kpl/agent/KplAgentApplication.java` | Entry point, registers DotenvConfig |
| `src/main/java/com/kpl/agent/service/KplAgentService.java` | AI agent core: intent recognition + tool routing |
| `src/main/java/com/kpl/agent/service/AgentIntentRecognizer.java` | LLM-based intent recognition |
| `src/main/java/com/kpl/agent/ai/ReportGenerator.java` | LLM report generation (sync + streaming) |
| `src/main/java/com/kpl/agent/tool/*.java` | Query tools (PlayerStats, HeroStats, TeamStats, MatchAnalysis, EquipStats) |
| `src/main/resources/application.yml` | All backend config with env var overrides |
| `src/main/resources/db/migration/V1__init_schema.sql` | DB schema (Flyway, V1-V10 migrations) |
| `frontend/src/App.vue` | Global layout, theme switching |
| `frontend/src/router/index.js` | All 12 routes (page views) |
| `frontend/vite.config.js` | Dev server, API proxy, SSE proxy config |

## Gotchas

- **Java version**: `pom.xml` says `<java.version>17</java.version>` but Dockerfile uses JDK 21. Local dev must use 17+.
- **MySQL port**: Default dev port is 13307 (not 3306) per `.env.example`. Docker compose uses `host.docker.internal:3306`.
- **Flyway**: Enabled with `baseline-on-migrate: true`, `validate-on-migrate: false`. New migrations go in `src/main/resources/db/migration/` as `V{n}__description.sql`.
- **Redis**: Used for query caching only (600s TTL). Health check disabled in actuator (`management.health.redis.enabled: false`).
- **SSE streaming**: Vite proxy has special SSE handling (disables buffering). Nginx config also sets `proxy_buffering off` for `/api/`.
- **Lombok**: Used throughout backend. Excluded from fat jar.
- **Scripts/**: Contains Python scraping scripts (wanplus, bilibili). These are standalone data collection tools, NOT part of the app. Don't run them when working on the main app.
- **Swagger UI**: Available at `http://localhost:9090/swagger-ui.html`.
- **Frontend build**: Uses `manualChunks` for vendor splitting (vue, element-plus, icons, markdown-it).
- **AI config defaults**: Model `mimo-v2.5` at `https://token-plan-cn.xiaomimimo.com/v1`. Requires `AI_API_KEY` env var.

## Testing

3 test files exist:
- `src/test/java/com/kpl/agent/service/LeagueQueryServiceTest.java`
- `src/test/java/com/kpl/agent/controller/ApiResponseTest.java`
- `src/test/java/com/kpl/agent/service/AgentIntentRecognizerTest.java`

Run with `mvn test`. No test infrastructure beyond Spring Boot Test.

## Style

- Backend: Lombok annotations, `@Slf4j`, `@RequiredArgsConstructor`. Package-per-layer: `controller/`, `service/`, `entity/`, `mapper/`, `tool/`, `config/`, `api/`.
- Frontend: Vue 3 Composition API (`<script setup>`), Element Plus components. Composables in `composables/`. Styles in `styles/` with `tokens.css`.
- All UI text is in Chinese. Entity names use Chinese characters (e.g., "AG超玩会", "公孙离").
