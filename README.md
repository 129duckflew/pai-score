# 打牌记账 (Card Game Score Keeper)

[![Deploy on Railway](https://railway.app/button.svg)](https://railway.app/new?template=https%3A%2F%2Fgithub.com%2F129duckflew%2Fpai-score)

一个基于 Socket.IO 实时通信的打牌记账工具，支持多人在线记分、房间管理、自由记分、断线重连。

## 近期更新摘要

- **超级管理员 Portal** — 新增 `/admin` 控制台，支持管理员密码登录、用户 CRUD/token 查看、在线状态、房间分页搜索、房间日志、强制关闭全部活跃房间、运行指标和 Spring Boot 最近日志查看。
- **Tailwind 前端重设计** — 登录、Lobby、房间、历史记录等页面统一升级为 Vue 3 + Tailwind 的移动端友好界面。
- **友好房间名** — 房间默认显示为创建者相关名称，历史记录、Lobby 和房间详情统一展示可读房间名。
- **解散房间历史保留** — 房主退出/解散房间后保留历史记录，用户仍可查看已解散房间的完整明细。
- **认证与移动端体验修复** — Socket token 失效会自动跳回登录页，并修复头像双击触发页面缩放的问题。

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
- **Source:** GitHub (`129duckflew/pai-score`, branch `main`)
- **Build:** Dockerfile at project root — multi-stage Maven build (JDK 21) producing a JRE runtime image
- **Ports:**
  - `PORT`（默认 8081）— REST API（Tomcat）
  - `SOCKETIO_PORT`（默认 8089）— Socket.IO 实时通信（netty-socketio）
- **Profile:** `SPRING_PROFILES_ACTIVE=railway` switches from H2 to PostgreSQL
- **Database:** Connects via Railway-injected env vars (`PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`)
- **Admin Secret:** `ADMIN_PASSWORD` is injected as a Railway/GitHub Secret and is used by `/admin`

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
// Backend env: SPRING_PROFILES_ACTIVE, PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE, ADMIN_PASSWORD
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
- **友好房间名** — 自动生成可读房间名称，列表和历史记录统一展示
- **自由记分** — 点击其他玩家头像弹框记分，支持备注，每笔记录带时间戳
- **断线重连** — token 存 localStorage，WebSocket 指数退避自动重连，恢复房间状态
- **历史记录** — 查看参与过的所有房间（进行中/已结束/已解散）和完整记分明细
- **超级管理员** — `/admin` 管理用户、房间、日志和运行状态，密码通过 `ADMIN_PASSWORD` Secret 注入
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
| `Room` | id, roomCode (6位), hostId, name, status (WAITING/PLAYING/FINISHED/DISBANDED) | 牌局房间 |
| `RoomPlayer` | id, roomId, userId, totalScore | 玩家-房间关联 |
| `ScoreEntry` | id, roomId, targetPlayerId, score, note, createdAt | 自由记分条目（带时间戳） |

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users/register` | 注册/登录 `{"username":"xxx"}` → 返回 userId + token |
| GET | `/api/users/{userId}/history` | 用户参与过的房间列表 |
| GET | `/api/rooms/{roomCode}/history` | 房间完整牌局记录（轮次+记分+排名） |
| POST | `/api/admin/login` | 超级管理员登录，密码来自 `ADMIN_PASSWORD` |
| GET | `/api/admin/users` | 分页查看用户、在线状态和 token |
| POST/PUT/DELETE | `/api/admin/users` / `/api/admin/users/{id}` | 管理用户 |
| GET | `/api/admin/rooms` | 分页搜索所有房间，支持状态筛选 |
| GET | `/api/admin/rooms/{roomCode}/history` | 管理端查看房间日志记录 |
| POST | `/api/admin/rooms/close-all` | 强制关闭所有等待中/进行中的房间 |
| GET | `/api/admin/runtime` | 查看 CPU、内存、线程、在线用户等运行状态 |
| GET | `/api/admin/logs` | 查看 Spring Boot 最近运行日志 |

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
pai-score/
├── Dockerfile                          # 后端 Dockerfile (Maven + JRE)
├── docker-compose.yml                  # Docker 编排
├── pom.xml                             # Maven 依赖
├── src/main/java/com/example/
│   ├── HelloApplication.java
│   ├── config/                         # 管理端日志缓冲 appender
│   ├── entity/                         # JPA 实体
│   ├── repository/                     # 数据访问层
│   ├── service/                        # 业务逻辑（RoomService, GameService, UserService, AdminAuthService）
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
        │   ├── HistoryView.vue         # 历史记录
        │   └── AdminView.vue           # 超级管理员 Portal
        └── services/
            ├── api.js                  # REST 调用
            ├── admin-api.js            # 管理端 REST 调用
            └── socketio.js             # Socket.IO 客户端 + 自动重连
```
