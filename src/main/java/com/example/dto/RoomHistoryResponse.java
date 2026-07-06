package com.example.dto;

import java.util.List;

public class RoomHistoryResponse {
    private String roomCode;
    private String name;
    private String status;
    private int feeAmount;
    private Long feePayerId;
    private String feePayerName;
    private List<PlayerInfo> players;
    private List<RoomFeeShareInfo> roomFeeShares;
    private List<EntryInfo> entries;
    private List<SettlementTransferInfo> settlementTransfers;

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFeeAmount() { return feeAmount; }
    public void setFeeAmount(int feeAmount) { this.feeAmount = feeAmount; }
    public Long getFeePayerId() { return feePayerId; }
    public void setFeePayerId(Long feePayerId) { this.feePayerId = feePayerId; }
    public String getFeePayerName() { return feePayerName; }
    public void setFeePayerName(String feePayerName) { this.feePayerName = feePayerName; }
    public List<PlayerInfo> getPlayers() { return players; }
    public void setPlayers(List<PlayerInfo> players) { this.players = players; }
    public List<RoomFeeShareInfo> getRoomFeeShares() { return roomFeeShares; }
    public void setRoomFeeShares(List<RoomFeeShareInfo> roomFeeShares) { this.roomFeeShares = roomFeeShares; }
    public List<EntryInfo> getEntries() { return entries; }
    public void setEntries(List<EntryInfo> entries) { this.entries = entries; }
    public List<SettlementTransferInfo> getSettlementTransfers() { return settlementTransfers; }
    public void setSettlementTransfers(List<SettlementTransferInfo> settlementTransfers) { this.settlementTransfers = settlementTransfers; }

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

    public static class RoomFeeShareInfo {
        private Long playerId;
        private Long userId;
        private String playerName;
        private int amount;

        public Long getPlayerId() { return playerId; }
        public void setPlayerId(Long playerId) { this.playerId = playerId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
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
        private boolean reverted;
        private String revertedAt;
        private Long revertedByUserId;
        private Long revertOfEntryId;

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
        public boolean isReverted() { return reverted; }
        public void setReverted(boolean reverted) { this.reverted = reverted; }
        public String getRevertedAt() { return revertedAt; }
        public void setRevertedAt(String revertedAt) { this.revertedAt = revertedAt; }
        public Long getRevertedByUserId() { return revertedByUserId; }
        public void setRevertedByUserId(Long revertedByUserId) { this.revertedByUserId = revertedByUserId; }
        public Long getRevertOfEntryId() { return revertOfEntryId; }
        public void setRevertOfEntryId(Long revertOfEntryId) { this.revertOfEntryId = revertOfEntryId; }
    }

    public static class SettlementTransferInfo {
        private Long fromPlayerId;
        private String fromPlayerName;
        private Long toPlayerId;
        private String toPlayerName;
        private int amount;

        public Long getFromPlayerId() { return fromPlayerId; }
        public void setFromPlayerId(Long fromPlayerId) { this.fromPlayerId = fromPlayerId; }
        public String getFromPlayerName() { return fromPlayerName; }
        public void setFromPlayerName(String fromPlayerName) { this.fromPlayerName = fromPlayerName; }
        public Long getToPlayerId() { return toPlayerId; }
        public void setToPlayerId(Long toPlayerId) { this.toPlayerId = toPlayerId; }
        public String getToPlayerName() { return toPlayerName; }
        public void setToPlayerName(String toPlayerName) { this.toPlayerName = toPlayerName; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
    }
}
