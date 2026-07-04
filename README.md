# 打牌记账 (Card Game Score Keeper)

一个基于 Socket.IO 实时通信的打牌记账工具，支持多人在线记分、房间管理、自由记分、断线重连。

## 技术栈

| 层 | 技术 |
|--|------|
| 后端 | Spring Boot 4.0.7 + Java 21 |
| 数据库 | H2 (文件存储) + Spring Data JPA |
| 实时通信 | Socket.IO (netty-socketio 2.0 + socket.io-client 3.x) |
| 前端 | Vue 3 + Vite (Composition API) |
| 部署 | Railway (IaC) + Docker + nginx + PostgreSQL |

## 架构

Backend 有两个端口：
- **REST API**（Tomcat, `${PORT}`，默认 8081）
- **Socket.IO**（netty-socketio, `${SOCKETIO_PORT}`，默认 8089）

前端 nginx 将 `/api/` 代理到 REST 端口，`/socket.io/` 代理到 Socket.IO 端口。

```
           Local Development
┌──────────────┐     ┌─────────────────┐     ┌────────────────────┐
│  浏览器       │────▶│  Vite dev proxy  │────▶│ Backend            │
│ Vue 3 SPA     │     │  /api → :8081    │     │ :8081 Tomcat (REST)│
│               │◀────│  /socket.io/    │◀────│ :8089 netty-socket │
└──────────────┘     │  → :8089         │     │     (Socket.IO)    │
      :5173           └─────────────────┘     │ + H2 DB            │
                                               └────────────────────┘

```

```
           Railway Deployment
┌──────────────┐     ┌──────────────────┐     ┌────────────────────┐     ┌────────────┐
│  浏览器       │────▶│  nginx           │────▶│ Backend             │────▶│ PostgreSQL │
│ Vue 3 SPA     │     │  (frontend)      │     │ :PORT (Tomcat/REST) │     │ (managed)  │
│               │◀────│  serve SPA       │◀────│ :SOCKETIO_PORT      │     │            │
└──────────────┘     │  /api/ → backend  │     │ (netty-socketio)    │     │ Volume:5GB │
                     │  /socket.io/      │     │ JPA                 │     └────────────┘
                     │    → backend      │     └────────────────────┘
                     └──────────────────┘
```

## Railway Deployment

The project is deployed on [Railway](https://railway.app) using Infrastructure-as-Code (`.railway/railway.ts`) with three resources:

### 1. PostgreSQL Database
- Managed PostgreSQL instance with a 5GB persistent volume in `us-west2`
- Disk usage alerts at 80%, 95%, and 100%

### 2. Backend (Spring Boot)
- **Source:** GitHub (`129duckflew/springboot-hello`, branch `main`)
- **Build:** Dockerfile at project root — multi-stage Maven build (JDK 21) producing a JRE runtime image
- **Ports:**
  - `PORT`（默认 8081）— REST API（Tomcat）
  - `SOCKETIO_PORT`（默认 8089）— Socket.IO 实时通信（netty-socketio）
- **Profile:** `SPRING_PROFILES_ACTIVE=railway` switches from H2 to PostgreSQL
- **Database:** Connects via Railway-injected env vars (`PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`)

### 3. Frontend (nginx + Vue 3 SPA)
- **Source:** GitHub, root directory `frontend/`
- **Build:** Multi-stage Dockerfile — `node:22-alpine` builds the Vite project, then serves via `nginx:stable-alpine`
- **Runtime:** `entrypoint.sh` uses `envsubst` to inject `BACKEND_HOST` and `BACKEND_PORT` into `nginx.conf.template`
- **Auto-sleep:** `sleepApplication: true` to save credits when idle
- **Reverse proxy rules:**
  - `/api/*` → `proxy_pass` to backend REST port
  - `/socket.io/*` → `proxy_pass` with WebSocket Upgrade headers to backend Socket.IO port
  - `/*` → serve static SPA files, fallback to `/index.html`

### Communication Flow
1. Browser hits Railway's public URL → routed to the Frontend (nginx) container
2. Nginx serves the Vue SPA static files directly
3. API calls (`/api/*`) are reverse-proxied to the Backend REST port
4. Socket.IO connections (`/socket.io/*`) are proxied with Upgrade headers to the Backend Socket.IO port
5. Backend connects to the Railway-managed PostgreSQL database
6. Frontend ↔ Backend communication uses Railway's internal private network (`RAILWAY_PRIVATE_DOMAIN`)

### Infrastructure as Code
The deployment is defined in `.railway/railway.ts` using the `railway/iac` module:
```ts
// Key resources: Postgres database, Backend service, Frontend service
// Backend env: SPRING_PROFILES_ACTIVE, PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE
// Frontend env: BACKEND_HOST (from backend.env.RAILWAY_PRIVATE_DOMAIN), BACKEND_PORT
```

### Useful Commands
```bash
railway config plan          # Preview infrastructure changes
railway config plan --json   # Machine-readable preview
railway config apply         # Apply changes
```

---

## 核心需求

- **免密注册** — 输入用户名即可，服务端生成 UUID token + 随机 Emoji 头像持久化身份
- **房间管理** — 创建房间（生成 6 位邀请码）/ 输入邀请码加入 / 离开
- **自由记分** — 点击其他玩家头像弹框记分，支持备注，每笔记录带时间戳
- **断线重连** — token 存 localStorage，WebSocket 指数退避自动重连，恢复房间状态
- **历史记录** — 查看参与过的所有房间（进行中/已结束）和完整记分明细
- **最多 8 人** — 每间房上限 8 名玩家
- **禁止自记分** — 不能对自己转分

## 快速启动

### 方式一：本地开发

```bash
# 终端 1 — 启动后端
mvn spring-boot:run

# 终端 2 — 启动前端
cd frontend && npm run dev
```

浏览器打开 `http://localhost:5173`

### 方式二：Docker Compose

```bash
docker compose up -d
```

浏览器打开 `http://localhost:8083`

> 如果端口冲突，可在 `docker-compose.yml` 中修改 `ports` 映射

## 数据模型

| 实体 | 关键字段 | 说明 |
|------|---------|------|
| `User` | id, username (unique), token (UUID), activeRoomCode | 用户身份 |
| `Room` | id, roomCode (6位), hostId, status (WAITING/PLAYING/FINISHED) | 牌局房间 |
| `RoomPlayer` | id, roomId, userId, totalScore | 玩家-房间关联 |
| `ScoreEntry` | id, roomId, targetPlayerId, score, note, createdAt | 自由记分条目（带时间戳） |

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users/register` | 注册/登录 `{"username":"xxx"}` → 返回 userId + token |
| GET | `/api/users/{userId}/history` | 用户参与过的房间列表 |
| GET | `/api/rooms/{roomCode}/history` | 房间完整牌局记录（轮次+记分+排名） |

## Socket.IO 消息协议

连接: `http://localhost:8089?token={token}`（Socket.IO 协议，自动升级到 WebSocket）

消息格式沿用旧的 JSON `{ type, ... }` 结构，Socket.IO 事件名即 `type` 字段值：

### Client → Server

```json
{ "type": "CREATE_ROOM" }
{ "type": "JOIN_ROOM", "roomCode": "ABC123" }
{ "type": "START_GAME", "roomCode": "ABC123" }
{ "type": "SUBMIT_SCORE", "roomCode": "ABC123", "targetPlayerId": 1, "score": 10, "note": "自摸" }
{ "type": "END_GAME", "roomCode": "ABC123" }
{ "type": "LEAVE_ROOM", "roomCode": "ABC123" }
```

### Server → Client

```json
{ "type": "AUTH_OK", "userId": 1, "username": "xxx" }
{ "type": "ROOM_CREATED", "roomCode": "ABC123", "players": [...] }
{ "type": "ROOM_JOINED", "roomCode": "ABC123", "players": [...] }
{ "type": "ROOM_STATE", "roomCode": "...", "status": "...", "players": [...], "entries": [...] }
{ "type": "PLAYER_LIST", "players": [...] }
{ "type": "GAME_STARTED", "players": [...] }
{ "type": "SCORE_ADDED", "entry": {...}, "players": [...] }
{ "type": "GAME_OVER", "players": [...], "entries": [...] }
{ "type": "ERROR", "message": "..." }
```

## 项目结构

```
springboot-hello/
├── Dockerfile                          # 后端 Dockerfile (Maven + JRE)
├── docker-compose.yml                  # Docker 编排
├── pom.xml                             # Maven 依赖
├── src/main/java/com/example/
│   ├── HelloApplication.java
│   ├── entity/                         # JPA 实体
│   ├── repository/                     # 数据访问层
│   ├── service/                        # 业务逻辑（RoomService, GameService, UserService）
│   ├── websocket/                      # Socket.IO 处理
│   │   ├── SocketIOConfig.java         # Socket.IO 服务器 Bean（认证 + 端口配置）
│   │   └── SocketIOEventListener.java  # 事件处理器（@OnConnect/Disconnect/Event）
│   ├── controller/                     # REST API
│   └── dto/                            # 数据传输对象
├── src/main/resources/
│   └── application.properties          # H2 + 端口配置
└── frontend/
    ├── Dockerfile                      # 前端 Dockerfile (Node + nginx)
    ├── nginx.conf.template             # nginx 反向代理配置
    ├── entrypoint.sh                   # 环境变量注入
    └── src/
        ├── views/                      # 页面组件
        │   ├── LoginView.vue           # 注册/登录
        │   ├── LobbyView.vue           # 创建/加入房间
        │   ├── RoomView.vue            # 记分面板
        │   └── HistoryView.vue         # 历史记录
        └── services/
            ├── api.js                  # REST 调用
            └── socketio.js             # Socket.IO 客户端 + 自动重连
```
