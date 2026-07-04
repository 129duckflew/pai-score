package com.example.service;

import com.example.entity.Room;
import com.example.entity.RoomPlayer;
import com.example.entity.User;
import com.example.repository.RoomPlayerRepository;
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
class ScoreConservationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoomPlayerRepository playerRepository;

    @Test
    void twoPlayersSumIsZero() {
        User a = userService.register("Alice");
        User b = userService.register("Bob");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());
        gameService.startGame(a.getId(), room.getRoomCode());

        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        gameService.submitScore(a.getId(), room.getRoomCode(), bP.getId(), 5, "t1");

        RoomPlayer aP = playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get();
        bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        assertThat(aP.getTotalScore()).isEqualTo(-5);
        assertThat(bP.getTotalScore()).isEqualTo(5);
        assertThat(aP.getTotalScore() + bP.getTotalScore()).isZero();
    }

    @Test
    void multipleTransactionsPreserveZeroSum() {
        User a = userService.register("A");
        User b = userService.register("B");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());
        gameService.startGame(a.getId(), room.getRoomCode());

        gameService.submitScore(a.getId(), room.getRoomCode(),
            playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get().getId(), 5, "");

        gameService.submitScore(b.getId(), room.getRoomCode(),
            playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get().getId(), 3, "");

        gameService.submitScore(a.getId(), room.getRoomCode(),
            playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get().getId(), -2, "");

        RoomPlayer aP = playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get();
        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        assertThat(aP.getTotalScore() + bP.getTotalScore()).isZero();
    }

    @Test
    void threePlayersConservation() {
        User a = userService.register("A"), b = userService.register("B"), c = userService.register("C");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());
        roomService.joinRoom(c.getId(), room.getRoomCode());
        gameService.startGame(a.getId(), room.getRoomCode());

        gameService.submitScore(a.getId(), room.getRoomCode(),
            playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get().getId(), 10, "");
        gameService.submitScore(b.getId(), room.getRoomCode(),
            playerRepository.findByRoomIdAndUserId(room.getId(), c.getId()).get().getId(), 3, "");
        gameService.submitScore(c.getId(), room.getRoomCode(),
            playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get().getId(), 7, "");

        RoomPlayer aP = playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get();
        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        RoomPlayer cP = playerRepository.findByRoomIdAndUserId(room.getId(), c.getId()).get();
        assertThat(aP.getTotalScore() + bP.getTotalScore() + cP.getTotalScore()).isZero();
    }

    @Test
    void negativeScoreTransfersFromTargetToSource() {
        User a = userService.register("A"), b = userService.register("B");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());
        gameService.startGame(a.getId(), room.getRoomCode());

        gameService.submitScore(a.getId(), room.getRoomCode(),
            playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get().getId(), -5, "");

        RoomPlayer aP = playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get();
        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        assertThat(aP.getTotalScore()).isEqualTo(5);
        assertThat(bP.getTotalScore()).isEqualTo(-5);
        assertThat(aP.getTotalScore() + bP.getTotalScore()).isZero();
    }
}
