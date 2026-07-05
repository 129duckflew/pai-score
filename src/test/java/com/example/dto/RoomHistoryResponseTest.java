package com.example.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomHistoryResponseTest {

    @Test
    void exposesFriendlyRoomNameAlongsideRoomCode() {
        RoomHistoryResponse response = new RoomHistoryResponse();

        response.setRoomCode("RBML7A");
        response.setName("Alice的房间");

        assertThat(response.getRoomCode()).isEqualTo("RBML7A");
        assertThat(response.getName()).isEqualTo("Alice的房间");
    }
}
