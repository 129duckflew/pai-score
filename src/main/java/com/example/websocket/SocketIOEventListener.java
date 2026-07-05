package com.example.websocket;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.annotation.*;
import com.example.entity.*;
import com.example.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(value = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOEventListener {

    private final SocketIOServer server;
    private final UserService userService;
    private final RoomService roomService;
    private final GameService gameService;
    private final Map<String, Set<Long>> onlineUsersByRoom = new ConcurrentHashMap<>();

    public SocketIOEventListener(@Lazy SocketIOServer server, UserService userService,
                                  RoomService roomService, GameService gameService) {
        this.server = server;
        this.userService = userService;
        this.roomService = roomService;
        this.gameService = gameService;
    }

    @OnConnect
    public void onConnect(SocketIOClient client) {
        Long userId = client.get("userId");
        String username = client.get("username");
        String avatar = client.get("avatar");

        if (userId == null) {
            client.sendEvent("ERROR", Map.of("message", "未认证", "type", "ERROR"));
            client.disconnect();
            return;
        }

        client.sendEvent("AUTH_OK", Map.of(
            "type", "AUTH_OK",
            "userId", userId,
            "username", username != null ? username : "?",
            "avatar", avatar
        ));

        User user = userService.findById(userId);
        if (user != null && user.getActiveRoomCode() != null) {
            Room room = roomService.findByCode(user.getActiveRoomCode());
            if (room != null && isActiveRoom(room)) {
                client.joinRoom(room.getRoomCode());
                markOnline(room.getRoomCode(), userId);
                broadcastPlayerList(room);
            }
        }
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        Long userId = client.get("userId");
        if (userId == null) return;

        User user = userService.findById(userId);
        if (user == null || user.getActiveRoomCode() == null) return;

        String roomCode = user.getActiveRoomCode();
        markOffline(roomCode, userId);
        Room room = roomService.findByCode(roomCode);
        if (room != null && isActiveRoom(room)) {
            broadcastPlayerList(room);
        }
    }

    @OnEvent("CREATE_ROOM")
    public void onCreateRoom(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            Room room = roomService.createRoom(userId);
            client.joinRoom(room.getRoomCode());
            markOnline(room.getRoomCode(), userId);

            User user = userService.findById(userId);
            user.setActiveRoomCode(room.getRoomCode());
            userService.updateUser(user);

            client.sendEvent("ROOM_CREATED", Map.of(
                "type", "ROOM_CREATED",
                "roomCode", room.getRoomCode(),
                "players", buildPlayerList(room.getId(), userId)
            ));
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    @OnEvent("JOIN_ROOM")
    public void onJoinRoom(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Room room = roomService.findByCode(roomCode);
            if (room == null) throw new RuntimeException("房间不存在");

            if ("PLAYING".equals(room.getStatus())) {
                RoomPlayer existing = roomService.findPlayer(room.getId(), userId);
                if (existing == null) throw new RuntimeException("游戏已开始");
            } else {
                roomService.joinRoom(userId, roomCode);
            }

            client.joinRoom(roomCode);
            markOnline(roomCode, userId);

            User user = userService.findById(userId);
            user.setActiveRoomCode(roomCode);
            userService.updateUser(user);

            client.sendEvent("ROOM_JOINED", Map.of(
                "type", "ROOM_JOINED",
                "roomCode", roomCode,
                "players", buildPlayerList(room.getId(), userId)
            ));

            broadcastPlayerList(room);

            User joinedUser = userService.findById(userId);
            server.getRoomOperations(roomCode).sendEvent("PLAYER_JOINED", Map.of(
                "type", "PLAYER_JOINED",
                "userId", userId,
                "username", joinedUser != null ? joinedUser.getUsername() : "?",
                "avatar", joinedUser != null ? joinedUser.getAvatar() : null
            ));
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    @OnEvent("LEAVE_ROOM")
    public void onLeaveRoom(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            roomService.leaveRoom(userId, roomCode);
            client.leaveRoom(roomCode);
            markOffline(roomCode, userId);

            User user = userService.findById(userId);
            if (user != null && roomCode.equals(user.getActiveRoomCode())) {
                user.setActiveRoomCode(null);
                userService.updateUser(user);
            }

            Room room = roomService.findByCode(roomCode);
            if (room == null || "DISBANDED".equals(room.getStatus())) {
                onlineUsersByRoom.remove(roomCode);
                server.getRoomOperations(roomCode).sendEvent("ROOM_DESTROYED", Map.of(
                    "type", "ROOM_DESTROYED",
                    "roomCode", roomCode
                ));
            } else {
                broadcastPlayerList(room);
            }
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    @OnEvent("START_GAME")
    public void onStartGame(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Room room = gameService.startGame(userId, roomCode);

            server.getRoomOperations(roomCode).sendEvent("GAME_STARTED", Map.of(
                "type", "GAME_STARTED",
                "players", buildPlayerList(room.getId(), null)
            ));
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    @OnEvent("SUBMIT_SCORE")
    public void onSubmitScore(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Long targetPlayerId = ((Number) msg.get("targetPlayerId")).longValue();
            Integer score = ((Number) msg.get("score")).intValue();
            String note = (String) msg.get("note");

            ScoreEntry entry = gameService.submitScore(userId, roomCode, targetPlayerId, score, note);
            Room room = roomService.findByCode(roomCode);

            server.getRoomOperations(roomCode).sendEvent("SCORE_ADDED", Map.of(
                "type", "SCORE_ADDED",
                "entry", entryToMap(entry),
                "players", buildPlayerList(room.getId(), null)
            ));
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    @OnEvent("END_GAME")
    public void onEndGame(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Room room = gameService.endGame(userId, roomCode);
            onlineUsersByRoom.remove(roomCode);

            for (RoomPlayer p : roomService.getPlayers(room.getId())) {
                User u = userService.findById(p.getUserId());
                if (u != null && roomCode.equals(u.getActiveRoomCode())) {
                    u.setActiveRoomCode(null);
                    userService.updateUser(u);
                }
            }

            List<Map<String, Object>> entriesData = gameService.getEntries(room.getId()).stream()
                .map(this::entryToMap)
                .collect(Collectors.toList());

            server.getRoomOperations(roomCode).sendEvent("GAME_OVER", Map.of(
                "type", "GAME_OVER",
                "players", buildPlayerList(room.getId(), null),
                "entries", entriesData
            ));
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    @OnEvent("GET_ROOM_STATE")
    public void onGetRoomState(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Room room = roomService.findByCode(roomCode);
            if (room == null) throw new RuntimeException("房间不存在");

            client.joinRoom(roomCode);
            if (isActiveRoom(room)) {
                markOnline(roomCode, userId);
            }
            sendRoomState(client, room);
            if (isActiveRoom(room)) {
                broadcastPlayerList(room);
            }
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    @OnEvent("ROLL_DICE")
    public void onRollDice(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        try {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

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

            server.getRoomOperations(roomCode).sendEvent("DICE_ROLL_RESULT", Map.of(
                "type", "DICE_ROLL_RESULT",
                "entry", entryToMap(entry),
                "roller", Map.of(
                    "userId", roller.getId(),
                    "username", roller.getUsername(),
                    "avatar", roller.getAvatar()
                ),
                "dice", List.of(d1, d2),
                "players", buildPlayerList(room.getId(), null)
            ));
        } catch (Exception e) {
            sendError(client, e.getMessage());
        }
    }

    // --- Helpers ---

    private void sendRoomState(SocketIOClient client, Room room) {
        List<Map<String, Object>> entriesData = gameService.getEntries(room.getId()).stream()
            .map(this::entryToMap)
            .collect(Collectors.toList());

        client.sendEvent("ROOM_STATE", Map.of(
            "type", "ROOM_STATE",
            "roomCode", room.getRoomCode(),
            "status", room.getStatus(),
            "hostId", room.getHostId(),
            "players", buildPlayerList(room.getId(), null),
            "entries", entriesData
        ));
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
                m.put("online", isOnline(roomId, p.getUserId()));
                return m;
            })
            .collect(Collectors.toList());
    }

    private boolean isActiveRoom(Room room) {
        return "WAITING".equals(room.getStatus()) || "PLAYING".equals(room.getStatus());
    }

    private void markOnline(String roomCode, Long userId) {
        onlineUsersByRoom.computeIfAbsent(roomCode, ignored -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    private void markOffline(String roomCode, Long userId) {
        Set<Long> onlineUsers = onlineUsersByRoom.get(roomCode);
        if (onlineUsers == null) return;
        onlineUsers.remove(userId);
        if (onlineUsers.isEmpty()) {
            onlineUsersByRoom.remove(roomCode);
        }
    }

    private boolean isOnline(Long roomId, Long userId) {
        Room room = roomService.findById(roomId);
        if (room == null) return false;
        return onlineUsersByRoom.getOrDefault(room.getRoomCode(), Set.of()).contains(userId);
    }

    private void broadcastPlayerList(Room room) {
        server.getRoomOperations(room.getRoomCode()).sendEvent("PLAYER_LIST", Map.of(
            "type", "PLAYER_LIST",
            "players", buildPlayerList(room.getId(), null)
        ));
    }

    private Map<String, Object> entryToMap(ScoreEntry e) {
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
    }

    private void sendError(SocketIOClient client, String message) {
        client.sendEvent("ERROR", Map.of("type", "ERROR", "message", message));
    }
}
