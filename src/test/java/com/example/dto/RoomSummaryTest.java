package com.example.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomSummaryTest {

    @Test
    void exposesFriendlyRoomNameAlongsideRoomCode() {
        RoomSummary summary = new RoomSummary("RBML7A", "Alice的房间", "WAITING", 2, "2026-07-05T12:00:00");

        assertThat(summary.getRoomCode()).isEqualTo("RBML7A");
        assertThat(summary.getName()).isEqualTo("Alice的房间");
    }
}
