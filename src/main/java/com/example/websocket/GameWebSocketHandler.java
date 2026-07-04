package com.example.websocket;

import com.example.entity.*;
import com.example.service.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final UserService userService;
    private final RoomService roomService;
    private final GameService gameService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameWebSocketHandler(SessionManager sessionManager, UserService userService,
                                 RoomService roomService, GameService gameService) {
        this.sessionManager = sessionManager;
        this.userService = userService;
        this.roomService = roomService;
        this.gameService = gameService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null) {
            closeSession(session, "缺少 token");
            return;
        }

        User user = userService.findByToken(token);
        if (user == null) {
            closeSession(session, "token 无效");
            return;
        }

        sessionManager.register(user.getId(), session);

        Map<String, Object> ack = new HashMap<>();
        ack.put("type", "AUTH_OK");
        ack.put("userId", user.getId());
        ack.put("username", user.getUsername());
        ack.put("avatar", user.getAvatar());
        sendToSession(session, ack);

        if (user.getActiveRoomCode() != null) {
            Room room = roomService.findByCode(user.getActiveRoomCode());
            if (room != null && !"FINISHED".equals(room.getStatus())) {
                sessionManager.joinRoom(room.getRoomCode(), user.getId());
                sendRoomState(session, room);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), HashMap.class);
            String type = (String) msg.get("type");
            if (type == null) return;

            Long userId = (Long) session.getAttributes().get("userId");
            if (userId == null) {
                sendError(session, "未认证");
                return;
            }

            switch (type) {
                case "CREATE_ROOM" -> handleCreateRoom(session, userId, msg);
                case "JOIN_ROOM" -> handleJoinRoom(session, userId, msg);
                case "LEAVE_ROOM" -> handleLeaveRoom(session, userId, msg);
                case "START_GAME" -> handleStartGame(session, userId, msg);
                case "SUBMIT_SCORE" -> handleSubmitScore(session, userId, msg);
                case "END_GAME" -> handleEndGame(session, userId, msg);
                case "GET_ROOM_STATE" -> handleGetRoomState(session, userId, msg);
                case "ROLL_DICE" -> handleRollDice(session, userId, msg);
                default -> sendError(session, "未知消息类型: " + type);
            }
        } catch (Exception e) {
            sendError(session, "消息格式错误: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.removeUserFromAllRooms(userId);
            sessionManager.unregister(session);
        }
    }

    private void handleCreateRoom(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            Room room = roomService.createRoom(userId);
            sessionManager.joinRoom(room.getRoomCode(), userId);

            User user = userService.findById(userId);
            user.setActiveRoomCode(room.getRoomCode());
            userService.updateUser(user);

            Map<String, Object> res = new HashMap<>();
            res.put("type", "ROOM_CREATED");
            res.put("roomCode", room.getRoomCode());
            res.put("players", buildPlayerList(room.getId(), userId));
            sendToSession(session, res);
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleJoinRoom(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            String roomCode = (String) msg.get("roomCode");
            Room room = roomService.findByCode(roomCode);
            if (room == null) throw new RuntimeException("房间不存在");

            if ("PLAYING".equals(room.getStatus())) {
                RoomPlayer existing = roomService.findPlayer(room.getId(), userId);
                if (existing != null) {
                    // Rejoining active game
                } else {
                    throw new RuntimeException("游戏已开始");
                }
            } else {
                roomService.joinRoom(userId, roomCode);
            }

            sessionManager.joinRoom(roomCode, userId);

            User user = userService.findById(userId);
            user.setActiveRoomCode(roomCode);
            userService.updateUser(user);

            Map<String, Object> res = new HashMap<>();
            res.put("type", "ROOM_JOINED");
            res.put("roomCode", roomCode);
            res.put("players", buildPlayerList(room.getId(), userId));
            sendToSession(session, res);

            sessionManager.broadcast(roomCode, Map.of(
                "type", "PLAYER_LIST",
                "players", buildPlayerList(room.getId(), null)
            ));
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleLeaveRoom(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            String roomCode = (String) msg.get("roomCode");
            roomService.leaveRoom(userId, roomCode);
            sessionManager.leaveRoom(roomCode, userId);

            User user = userService.findById(userId);
            if (user != null && roomCode.equals(user.getActiveRoomCode())) {
                user.setActiveRoomCode(null);
                userService.updateUser(user);
            }

            Room room = roomService.findByCode(roomCode);
            if (room == null) {
                sessionManager.broadcast(roomCode, Map.of(
                    "type", "ROOM_DESTROYED",
                    "roomCode", roomCode
                ));
                sessionManager.removeRoom(roomCode);
            } else {
                sessionManager.broadcast(roomCode, Map.of(
                    "type", "PLAYER_LIST",
                    "players", buildPlayerList(room.getId(), null)
                ));
            }
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleStartGame(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            String roomCode = (String) msg.get("roomCode");
            Room room = gameService.startGame(userId, roomCode);

            sessionManager.broadcast(roomCode, Map.of(
                "type", "GAME_STARTED",
                "players", buildPlayerList(room.getId(), null)
            ));
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleSubmitScore(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            String roomCode = (String) msg.get("roomCode");
            Long targetPlayerId = ((Number) msg.get("targetPlayerId")).longValue();
            Integer score = ((Number) msg.get("score")).intValue();
            String note = (String) msg.get("note");

            ScoreEntry entry = gameService.submitScore(userId, roomCode, targetPlayerId, score, note);
            Room room = roomService.findByCode(roomCode);

            Map<String, Object> entryData = new HashMap<>();
            entryData.put("id", entry.getId());
            entryData.put("sourcePlayerId", entry.getSourcePlayerId());
            entryData.put("targetPlayerId", entry.getTargetPlayerId());
            entryData.put("score", entry.getScore());
            entryData.put("note", entry.getNote());
            entryData.put("addedByUserId", entry.getAddedByUserId());
            entryData.put("type", entry.getType());
            entryData.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null);

            sessionManager.broadcast(roomCode, Map.of(
                "type", "SCORE_ADDED",
                "entry", entryData,
                "players", buildPlayerList(room.getId(), null)
            ));
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleEndGame(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            String roomCode = (String) msg.get("roomCode");
            Room room = gameService.endGame(userId, roomCode);

            for (RoomPlayer p : roomService.getPlayers(room.getId())) {
                User u = userService.findById(p.getUserId());
                if (u != null && roomCode.equals(u.getActiveRoomCode())) {
                    u.setActiveRoomCode(null);
                    userService.updateUser(u);
                }
            }

            List<ScoreEntry> allEntries = gameService.getEntries(room.getId());
            List<Map<String, Object>> entriesData = allEntries.stream().map(e -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", e.getId());
                m.put("sourcePlayerId", e.getSourcePlayerId());
                m.put("targetPlayerId", e.getTargetPlayerId());
                m.put("score", e.getScore());
                m.put("note", e.getNote());
                m.put("addedByUserId", e.getAddedByUserId());
                m.put("type", e.getType());
                m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                return m;
            }).collect(Collectors.toList());

            sessionManager.broadcast(roomCode, Map.of(
                "type", "GAME_OVER",
                "players", buildPlayerList(room.getId(), null),
                "entries", entriesData
            ));
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleGetRoomState(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            String roomCode = (String) msg.get("roomCode");
            Room room = roomService.findByCode(roomCode);
            if (room == null) throw new RuntimeException("房间不存在");
            sendRoomState(session, room);
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleRollDice(WebSocketSession session, Long userId, Map<String, Object> msg) {
        try {
            String roomCode = (String) msg.get("roomCode");
            ScoreEntry entry = gameService.rollDice(userId, roomCode);
            Room room = roomService.findByCode(roomCode);

            String note = entry.getNote();
            int d1 = 0, d2 = 0;
            if (note != null && note.contains("🎲")) {
                String[] parts = note.split("🎲");
                if (parts.length >= 3) {
                    d1 = Integer.parseInt(parts[1].trim());
                    d2 = Integer.parseInt(parts[2].trim());
                }
            }

            User roller = userService.findById(userId);

            Map<String, Object> entryData = new HashMap<>();
            entryData.put("id", entry.getId());
            entryData.put("sourcePlayerId", entry.getSourcePlayerId());
            entryData.put("targetPlayerId", entry.getTargetPlayerId());
            entryData.put("score", entry.getScore());
            entryData.put("note", entry.getNote());
            entryData.put("addedByUserId", entry.getAddedByUserId());
            entryData.put("type", entry.getType());
            entryData.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null);

            sessionManager.broadcast(roomCode, Map.of(
                "type", "DICE_ROLL_RESULT",
                "entry", entryData,
                "roller", Map.of(
                    "userId", roller.getId(),
                    "username", roller.getUsername(),
                    "avatar", roller.getAvatar()
                ),
                "dice", List.of(d1, d2),
                "players", buildPlayerList(room.getId(), null)
            ));
        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void sendRoomState(WebSocketSession session, Room room) {
        List<ScoreEntry> entries = gameService.getEntries(room.getId());
        List<Map<String, Object>> entriesData = entries.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("sourcePlayerId", e.getSourcePlayerId());
            m.put("targetPlayerId", e.getTargetPlayerId());
            m.put("score", e.getScore());
            m.put("note", e.getNote());
            m.put("addedByUserId", e.getAddedByUserId());
            m.put("type", e.getType());
            m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("type", "ROOM_STATE");
        res.put("roomCode", room.getRoomCode());
        res.put("status", room.getStatus());
        res.put("hostId", room.getHostId());
        res.put("players", buildPlayerList(room.getId(), null));
        res.put("entries", entriesData);
        sendToSession(session, res);
    }

    private List<Map<String, Object>> buildPlayerList(Long roomId, Long excludeUserId) {
        return roomService.getPlayersOrdered(roomId).stream()
            .filter(p -> excludeUserId == null || !p.getUserId().equals(excludeUserId))
            .map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("playerId", p.getId());
                m.put("userId", p.getUserId());
                User u = userService.findById(p.getUserId());
                m.put("username", u != null ? u.getUsername() : "?");
                m.put("avatar", u != null ? u.getAvatar() : null);
                m.put("totalScore", p.getTotalScore());
                m.put("joinedAt", p.getJoinedAt() != null ? p.getJoinedAt().toString() : null);
                return m;
            })
            .collect(Collectors.toList());
    }

    private String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2 && "token".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private void sendToSession(WebSocketSession session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendError(WebSocketSession session, String msg) {
        sendToSession(session, Map.of("type", "ERROR", "message", msg));
    }

    private void closeSession(WebSocketSession session, String reason) {
        try {
            sendError(session, reason);
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {}
    }
}
