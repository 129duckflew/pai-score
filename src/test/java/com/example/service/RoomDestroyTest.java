package com.example.service;

import com.example.entity.Room;
import com.example.entity.User;
import com.example.repository.RoomPlayerRepository;
import com.example.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoomDestroyTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomPlayerRepository playerRepository;

    @Test
    void hostLeaveDestroysRoomAndDeletesAllPlayers() {
        User host = userService.register("Host");
        User guest = userService.register("Guest");
        Room room = roomService.createRoom(host.getId());
        roomService.joinRoom(guest.getId(), room.getRoomCode());

        roomService.leaveRoom(host.getId(), room.getRoomCode());

        assertThat(roomRepository.findByRoomCode(room.getRoomCode())).isEmpty();
        assertThat(playerRepository.findByRoomId(room.getId())).isEmpty();
    }

    @Test
    void nonHostLeaveDoesNotDestroyRoom() {
        User host = userService.register("Host");
        User guest = userService.register("Guest");
        Room room = roomService.createRoom(host.getId());
        roomService.joinRoom(guest.getId(), room.getRoomCode());

        roomService.leaveRoom(guest.getId(), room.getRoomCode());

        assertThat(roomRepository.findByRoomCode(room.getRoomCode())).isPresent();
        assertThat(playerRepository.findByRoomId(room.getId())).hasSize(1);
    }

    @Test
    void lastPlayerLeaveDestroysRoom() {
        User host = userService.register("Host");
        Room room = roomService.createRoom(host.getId());

        roomService.leaveRoom(host.getId(), room.getRoomCode());

        assertThat(roomRepository.findByRoomCode(room.getRoomCode())).isEmpty();
    }

    @Test
    void destroyedRoomNotInActiveRooms() {
        User host = userService.register("Host");
        User guest = userService.register("Guest");
        Room room = roomService.createRoom(host.getId());
        roomService.joinRoom(guest.getId(), room.getRoomCode());

        roomService.leaveRoom(host.getId(), room.getRoomCode());

        assertThat(roomService.getActiveRooms())
            .noneMatch(r -> r.getRoomCode().equals(room.getRoomCode()));
    }
}
