package com.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_transfers")
public class SettlementTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long fromPlayerId;

    @Column(nullable = false)
    private Long toPlayerId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getFromPlayerId() { return fromPlayerId; }
    public void setFromPlayerId(Long fromPlayerId) { this.fromPlayerId = fromPlayerId; }
    public Long getToPlayerId() { return toPlayerId; }
    public void setToPlayerId(Long toPlayerId) { this.toPlayerId = toPlayerId; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
