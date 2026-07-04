package com.example.service;

import com.example.entity.Room;
import com.example.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    @Test
    void getUserRooms_onlyReturnsRoomsJoinedByThatUser() {
        User u1 = userService.register("Alice");
        User u2 = userService.register("Bob");

        Room room = roomService.createRoom(u1.getId());

        List<Room> u1Rooms = roomService.getUserRooms(u1.getId());
        assertThat(u1Rooms).hasSize(1);
        assertThat(u1Rooms.get(0).getRoomCode()).isEqualTo(room.getRoomCode());

        List<Room> u2Rooms = roomService.getUserRooms(u2.getId());
        assertThat(u2Rooms).isEmpty();
    }

    @Test
    void getUserRooms_afterJoining_returnsRoom() {
        User u1 = userService.register("Alice");
        User u2 = userService.register("Bob");
        Room room = roomService.createRoom(u1.getId());

        roomService.joinRoom(u2.getId(), room.getRoomCode());

        List<Room> u2Rooms = roomService.getUserRooms(u2.getId());
        assertThat(u2Rooms).hasSize(1);
        assertThat(u2Rooms.get(0).getRoomCode()).isEqualTo(room.getRoomCode());
    }
}
