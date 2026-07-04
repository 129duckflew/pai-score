# 打牌记账 (Card Game Score Keeper)

一个基于 WebSocket 实时通信的打牌记账工具，支持多人在线记分、房间管理、自由记分、断线重连。

## 技术栈

| 层 | 技术 |
|--|------|
| 后端 | Spring Boot 4.0.7 + Java 21 |
| 数据库 | H2 (文件存储) + Spring Data JPA |
| 实时通信 | 原生 WebSocket (JSON 消息) |
| 前端 | Vue 3 + Vite (Composition API) |
| 部署 | Docker Compose + nginx |

## 架构

```
┌─────────────┐     ┌──────────────┐     ┌──────────┐
│  浏览器     │────▶│  nginx       │────▶│ Backend  │
│ Vue 3 SPA   │ WS  │  proxy /ws   │ WS  │ :8081    │
│             │◀────│  proxy /api  │◀────│ WebSocket│
└─────────────┘     └──────────────┘     │ + REST   │
      :8083                                + H2 DB  │
                                          └──────────┘
```

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

## WebSocket 消息协议

端点: `ws://localhost:8081/ws?token={token}`

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
│   ├── service/                        # 业务逻辑
│   ├── websocket/                      # WebSocket 处理
│   │   ├── WebSocketConfig.java        # 端点注册
│   │   ├── GameWebSocketHandler.java   # 消息路由
│   │   └── SessionManager.java         # 连接/房间管理
│   ├── controller/                     # REST API
│   └── dto/                            # 数据传输对象
├── src/main/resources/
│   └── application.properties          # H2 + 端口配置
└── frontend/
    ├── Dockerfile                      # 前端 Dockerfile (Node + nginx)
    ├── nginx.conf                      # nginx 反向代理配置
    └── src/
        ├── views/                      # 页面组件
        │   ├── LoginView.vue           # 注册/登录
        │   ├── LobbyView.vue           # 创建/加入房间
        │   ├── RoomView.vue            # 记分面板
        │   └── HistoryView.vue         # 历史记录
        └── services/
            ├── api.js                  # REST 调用
            └── websocket.js            # WebSocket 客户端 + 自动重连
```
