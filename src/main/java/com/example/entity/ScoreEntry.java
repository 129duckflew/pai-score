package com.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "score_entries")
public class ScoreEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    private Long sourcePlayerId;

    @Column(nullable = false)
    private Long targetPlayerId;

    @Column(nullable = false)
    private Long addedByUserId;

    @Column(nullable = false)
    private Integer score;

    @Column(length = 20)
    private String type = "SCORE";

    @Column(length = 200)
    private String note;

    @Column
    private Boolean reverted = false;

    private LocalDateTime revertedAt;

    private Long revertedByUserId;

    private Long revertOfEntryId;

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
    public Long getSourcePlayerId() { return sourcePlayerId; }
    public void setSourcePlayerId(Long sourcePlayerId) { this.sourcePlayerId = sourcePlayerId; }
    public Long getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(Long targetPlayerId) { this.targetPlayerId = targetPlayerId; }
    public Long getAddedByUserId() { return addedByUserId; }
    public void setAddedByUserId(Long addedByUserId) { this.addedByUserId = addedByUserId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Boolean getReverted() { return reverted; }
    public void setReverted(Boolean reverted) { this.reverted = reverted; }
    public LocalDateTime getRevertedAt() { return revertedAt; }
    public void setRevertedAt(LocalDateTime revertedAt) { this.revertedAt = revertedAt; }
    public Long getRevertedByUserId() { return revertedByUserId; }
    public void setRevertedByUserId(Long revertedByUserId) { this.revertedByUserId = revertedByUserId; }
    public Long getRevertOfEntryId() { return revertOfEntryId; }
    public void setRevertOfEntryId(Long revertOfEntryId) { this.revertOfEntryId = revertOfEntryId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
