package com.example.websocket;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.annotation.*;
import com.example.entity.*;
import com.example.service.*;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(value = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOEventListener {
    private static final Logger log = LoggerFactory.getLogger(SocketIOEventListener.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("pai-score-socketio");
    private static final TextMapGetter<Map<String, Object>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, Object> carrier) {
            return carrier != null ? carrier.keySet() : Set.of();
        }

        @Override
        public String get(Map<String, Object> carrier, String key) {
            if (carrier == null || key == null) return null;
            Object value = carrier.get(key);
            return value != null ? String.valueOf(value) : null;
        }
    };

    private final SocketIOServer server;
    private final UserService userService;
    private final RoomService roomService;
    private final GameService gameService;
    private final OnlineUserRegistry onlineUserRegistry;
    private final Map<String, Set<Long>> onlineUsersByRoom = new ConcurrentHashMap<>();

    public SocketIOEventListener(@Lazy SocketIOServer server, UserService userService,
                                  RoomService roomService, GameService gameService,
                                  OnlineUserRegistry onlineUserRegistry) {
        this.server = server;
        this.userService = userService;
        this.roomService = roomService;
        this.gameService = gameService;
        this.onlineUserRegistry = onlineUserRegistry;
    }

    @OnConnect
    public void onConnect(SocketIOClient client) {
        try (TraceContext.Scope ignored = TraceContext.open(null)) {
            Long userId = client.get("userId");
            String username = client.get("username");
            String avatar = client.get("avatar");
            TraceContext.put(TraceContext.USER_ID, userId);
            TraceContext.put(TraceContext.EVENT, "CONNECT");
            log.info("Socket connected userId={} username={}", userId, username);

            if (userId == null) {
                client.sendEvent("ERROR", Map.of("message", "未认证", "type", "ERROR", "traceId", TraceContext.currentTraceId()));
                client.disconnect();
                return;
            }

            onlineUserRegistry.markOnline(userId);

            client.sendEvent("AUTH_OK", Map.of(
                "type", "AUTH_OK",
                "traceId", TraceContext.currentTraceId(),
                "userId", userId,
                "username", username != null ? username : "?",
                "avatar", avatar
            ));

            User user = userService.findById(userId);
            if (user != null && user.getActiveRoomCode() != null) {
                Room room = roomService.findByCode(user.getActiveRoomCode());
                if (room != null && isActiveRoom(room)) {
                    TraceContext.put(TraceContext.ROOM_CODE, room.getRoomCode());
                    client.joinRoom(room.getRoomCode());
                    markOnline(room.getRoomCode(), userId);
                    broadcastPlayerList(room);
                }
            }
        }
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        try (TraceContext.Scope ignored = TraceContext.open(null)) {
            Long userId = client.get("userId");
            TraceContext.put(TraceContext.USER_ID, userId);
            TraceContext.put(TraceContext.EVENT, "DISCONNECT");
            log.info("Socket disconnected userId={}", userId);
            if (userId == null) return;
            onlineUserRegistry.markOffline(userId);

            User user = userService.findById(userId);
            if (user == null || user.getActiveRoomCode() == null) return;

            String roomCode = user.getActiveRoomCode();
            TraceContext.put(TraceContext.ROOM_CODE, roomCode);
            markOffline(roomCode, userId);
            Room room = roomService.findByCode(roomCode);
            if (room != null && isActiveRoom(room)) {
                broadcastPlayerList(room);
            }
        }
    }

    @OnEvent("CREATE_ROOM")
    public void onCreateRoom(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "CREATE_ROOM", () -> {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            Integer feeAmount = intFrom(msg != null ? msg.get("feeAmount") : null, 0);
            Room room = roomService.createRoom(userId, feeAmount);
            TraceContext.put(TraceContext.ROOM_CODE, room.getRoomCode());
            client.joinRoom(room.getRoomCode());
            markOnline(room.getRoomCode(), userId);

            User user = userService.findById(userId);
            user.setActiveRoomCode(room.getRoomCode());
            userService.updateUser(user);

            client.sendEvent("ROOM_CREATED", Map.of(
                "type", "ROOM_CREATED",
                "traceId", TraceContext.currentTraceId(),
                "roomCode", room.getRoomCode(),
                "roomName", roomDisplayName(room),
                "feeAmount", room.getFeeAmount() != null ? room.getFeeAmount() : 0,
                "players", buildPlayerList(room.getId(), userId)
            ));
        });
    }

    @OnEvent("JOIN_ROOM")
    public void onJoinRoom(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "JOIN_ROOM", () -> {
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
                "traceId", TraceContext.currentTraceId(),
                "roomCode", roomCode,
                "roomName", roomDisplayName(room),
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
        });
    }

    @OnEvent("LEAVE_ROOM")
    public void onLeaveRoom(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "LEAVE_ROOM", () -> {
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
        });
    }

    @OnEvent("START_GAME")
    public void onStartGame(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "START_GAME", () -> {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Room room = gameService.startGame(userId, roomCode);

            server.getRoomOperations(roomCode).sendEvent("GAME_STARTED", Map.of(
                "type", "GAME_STARTED",
                "players", buildPlayerList(room.getId(), null)
            ));
        });
    }

    @OnEvent("SUBMIT_SCORE")
    public void onSubmitScore(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "SUBMIT_SCORE", () -> {
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
        });
    }

    @OnEvent("SET_ROOM_FEE")
    public void onSetRoomFee(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "SET_ROOM_FEE", () -> {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Integer feeAmount = intFrom(msg.get("feeAmount"), 0);

            ScoreEntry entry = gameService.setRoomFee(userId, roomCode, feeAmount);
            Room room = roomService.findByCode(roomCode);

            server.getRoomOperations(roomCode).sendEvent("ROOM_FEE_UPDATED", Map.of(
                "type", "ROOM_FEE_UPDATED",
                "feeAmount", room.getFeeAmount() != null ? room.getFeeAmount() : 0,
                "entry", entryToMap(entry),
                "players", buildPlayerList(room.getId(), null)
            ));
        });
    }

    @OnEvent("REVERT_SCORE")
    public void onRevertScore(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "REVERT_SCORE", () -> {
            Long userId = client.get("userId");
            if (userId == null) { sendError(client, "未认证"); return; }

            String roomCode = (String) msg.get("roomCode");
            Long entryId = ((Number) msg.get("entryId")).longValue();

            ScoreEntry revertLog = gameService.revertScore(userId, roomCode, entryId);
            Room room = roomService.findByCode(roomCode);
            List<Map<String, Object>> entriesData = gameService.getEntries(room.getId()).stream()
                .map(this::entryToMap)
                .collect(Collectors.toList());

            server.getRoomOperations(roomCode).sendEvent("SCORE_REVERTED", Map.of(
                "type", "SCORE_REVERTED",
                "entry", entryToMap(revertLog),
                "entries", entriesData,
                "players", buildPlayerList(room.getId(), null)
            ));
        });
    }

    @OnEvent("END_GAME")
    public void onEndGame(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "END_GAME", () -> {
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
                "entries", entriesData,
                "settlementTransfers", settlementTransfersToMap(room.getId())
            ));
        });
    }

    @OnEvent("GET_ROOM_STATE")
    public void onGetRoomState(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "GET_ROOM_STATE", () -> {
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
        });
    }

    @OnEvent("ROLL_DICE")
    public void onRollDice(SocketIOClient client, AckRequest ack, Map<String, Object> msg) {
        withSocketTrace(client, msg, "ROLL_DICE", () -> {
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
        });
    }

    // --- Helpers ---

    private void sendRoomState(SocketIOClient client, Room room) {
        List<Map<String, Object>> entriesData = gameService.getEntries(room.getId()).stream()
            .map(this::entryToMap)
            .collect(Collectors.toList());

        client.sendEvent("ROOM_STATE", Map.of(
            "type", "ROOM_STATE",
            "roomCode", room.getRoomCode(),
            "roomName", roomDisplayName(room),
            "status", room.getStatus(),
            "hostId", room.getHostId(),
            "feeAmount", room.getFeeAmount() != null ? room.getFeeAmount() : 0,
            "players", buildPlayerList(room.getId(), null),
            "entries", entriesData,
            "settlementTransfers", settlementTransfersToMap(room.getId())
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

    private String roomDisplayName(Room room) {
        return room.getName() != null && !room.getName().isBlank() ? room.getName() : "房间 " + room.getRoomCode();
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
        m.put("reverted", Boolean.TRUE.equals(e.getReverted()));
        m.put("revertedAt", e.getRevertedAt() != null ? e.getRevertedAt().toString() : null);
        m.put("revertedByUserId", e.getRevertedByUserId());
        m.put("revertOfEntryId", e.getRevertOfEntryId());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    private List<Map<String, Object>> settlementTransfersToMap(Long roomId) {
        return gameService.getSettlementTransfers(roomId).stream()
            .map(t -> {
                Map<String, Object> m = new HashMap<>();
                m.put("fromPlayerId", t.getFromPlayerId());
                m.put("fromPlayerName", playerName(roomId, t.getFromPlayerId()));
                m.put("toPlayerId", t.getToPlayerId());
                m.put("toPlayerName", playerName(roomId, t.getToPlayerId()));
                m.put("amount", t.getAmount());
                return m;
            })
            .collect(Collectors.toList());
    }

    private String playerName(Long roomId, Long playerId) {
        return roomService.getPlayers(roomId).stream()
            .filter(player -> player.getId().equals(playerId))
            .findFirst()
            .map(player -> userService.findById(player.getUserId()))
            .map(User::getUsername)
            .orElse("?");
    }

    private Integer intFrom(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) return Integer.parseInt(text);
        return defaultValue;
    }

    private void sendError(SocketIOClient client, String message) {
        client.sendEvent("ERROR", Map.of(
            "type", "ERROR",
            "traceId", TraceContext.currentTraceId(),
            "message", message != null ? message : "请求失败"
        ));
    }

    private void withSocketTrace(SocketIOClient client, Map<String, Object> msg, String eventName, SocketAction action) {
        Context parentContext = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), msg, MAP_GETTER);
        Span span = tracer.spanBuilder("socket." + eventName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.SERVER)
            .startSpan();
        try (Scope spanScope = span.makeCurrent();
             TraceContext.Scope ignored = TraceContext.open(stringFrom(msg, "requestId"), stringFrom(msg, "traceparent"))) {
            Long userId = client.get("userId");
            String roomCode = stringFrom(msg, "roomCode");
            TraceContext.putTrace(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
            TraceContext.put(TraceContext.USER_ID, userId);
            TraceContext.put(TraceContext.ROOM_CODE, roomCode);
            TraceContext.put(TraceContext.EVENT, eventName);
            span.setAttribute("app.event", eventName);
            if (userId != null) span.setAttribute("app.user_id", userId);
            if (roomCode != null && !roomCode.isBlank()) span.setAttribute("app.room_code", roomCode);
            span.setAttribute("socket.client_id", client.getSessionId().toString());
            long started = System.currentTimeMillis();
            log.info("Socket event started event={} userId={} roomCode={}", eventName, userId, roomCode);
            try {
                action.run();
                span.setStatus(StatusCode.OK);
                log.info("Socket event completed event={} userId={} roomCode={} durationMs={}",
                    eventName, userId, roomCode, System.currentTimeMillis() - started);
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage() != null ? e.getMessage() : "Socket event failed");
                log.error("Socket event failed event={} userId={} roomCode={} durationMs={}",
                    eventName, userId, roomCode, System.currentTimeMillis() - started, e);
                sendError(client, e.getMessage());
            }
        } finally {
            span.end();
        }
    }

    private String stringFrom(Map<String, Object> msg, String key) {
        if (msg == null) return null;
        Object value = msg.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    @FunctionalInterface
    private interface SocketAction {
        void run() throws Exception;
    }

    public void broadcastRoomsDestroyed(List<String> roomCodes) {
        for (String roomCode : roomCodes) {
            onlineUsersByRoom.remove(roomCode);
            server.getRoomOperations(roomCode).sendEvent("ROOM_DESTROYED", Map.of(
                "type", "ROOM_DESTROYED",
                "roomCode", roomCode
            ));
        }
    }
}
