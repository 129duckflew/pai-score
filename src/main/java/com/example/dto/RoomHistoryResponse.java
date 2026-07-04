package com.example.dto;

import java.util.List;

public class RoomHistoryResponse {
    private String roomCode;
    private String status;
    private List<PlayerInfo> players;
    private List<EntryInfo> entries;

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<PlayerInfo> getPlayers() { return players; }
    public void setPlayers(List<PlayerInfo> players) { this.players = players; }
    public List<EntryInfo> getEntries() { return entries; }
    public void setEntries(List<EntryInfo> entries) { this.entries = entries; }

    public static class PlayerInfo {
        private Long playerId;
        private Long userId;
        private String username;
        private String avatar;
        private int totalScore;

        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public int getTotalScore() { return totalScore; }
        public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    }

    public static class EntryInfo {
        private Long id;
        private Long sourcePlayerId;
        private String sourcePlayerName;
        private Long targetPlayerId;
        private String targetPlayerName;
        private int score;
        private String type;
        private String note;
        private String addedByUsername;
        private String createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getSourcePlayerId() { return sourcePlayerId; }
        public void setSourcePlayerId(Long sourcePlayerId) { this.sourcePlayerId = sourcePlayerId; }
        public String getSourcePlayerName() { return sourcePlayerName; }
        public void setSourcePlayerName(String sourcePlayerName) { this.sourcePlayerName = sourcePlayerName; }
        public Long getTargetPlayerId() { return targetPlayerId; }
        public void setTargetPlayerId(Long targetPlayerId) { this.targetPlayerId = targetPlayerId; }
        public String getTargetPlayerName() { return targetPlayerName; }
        public void setTargetPlayerName(String targetPlayerName) { this.targetPlayerName = targetPlayerName; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getAddedByUsername() { return addedByUsername; }
        public void setAddedByUsername(String addedByUsername) { this.addedByUsername = addedByUsername; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
}
