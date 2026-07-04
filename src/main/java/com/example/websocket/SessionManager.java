package com.example.websocket;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class SessionManager {

    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> roomPlayers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void register(Long userId, WebSocketSession session) {
        WebSocketSession old = userSessions.put(userId, session);
        if (old != null && old.isOpen()) {
            try { old.close(); } catch (IOException ignored) {}
        }
        session.getAttributes().put("userId", userId);
    }

    public void unregister(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId, session);
        }
    }

    public void joinRoom(String roomCode, Long userId) {
        roomPlayers.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>()).add(userId);
    }

    public void leaveRoom(String roomCode, Long userId) {
        Set<Long> players = roomPlayers.get(roomCode);
        if (players != null) {
            players.remove(userId);
            if (players.isEmpty()) {
                roomPlayers.remove(roomCode);
            }
        }
    }

    public void removeUserFromAllRooms(Long userId) {
        roomPlayers.values().forEach(p -> p.remove(userId));
    }

    public void broadcast(String roomCode, Object message) {
        Set<Long> userIds = roomPlayers.get(roomCode);
        if (userIds == null) return;
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage text = new TextMessage(json);
            for (Long uid : userIds) {
                WebSocketSession session = userSessions.get(uid);
                if (session != null && session.isOpen()) {
                    try { session.sendMessage(text); } catch (IOException ignored) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendToUser(Long userId, Object message) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
