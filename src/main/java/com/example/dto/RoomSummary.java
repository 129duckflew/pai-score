package com.example.dto;

public class RoomSummary {
    private String roomCode;
    private String name;
    private String status;
    private int playerCount;
    private String createdAt;
    private int feeAmount;

    public RoomSummary(String roomCode, String name, String status, int playerCount, String createdAt) {
        this(roomCode, name, status, playerCount, createdAt, 0);
    }

    public RoomSummary(String roomCode, String name, String status, int playerCount, String createdAt, int feeAmount) {
        this.roomCode = roomCode;
        this.name = name;
        this.status = status;
        this.playerCount = playerCount;
        this.createdAt = createdAt;
        this.feeAmount = feeAmount;
    }

    public String getRoomCode() { return roomCode; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public int getPlayerCount() { return playerCount; }
    public String getCreatedAt() { return createdAt; }
    public int getFeeAmount() { return feeAmount; }
}
