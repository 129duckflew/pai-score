package com.example.service;

import com.example.entity.Room;
import com.example.entity.RoomPlayer;
import com.example.entity.ScoreEntry;
import com.example.entity.SettlementTransfer;
import com.example.entity.User;
import com.example.repository.RoomPlayerRepository;
import com.example.repository.ScoreEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private ScoreEntryRepository entryRepository;

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

    @Test
    void revertScoreWithinOneMinuteRestoresTotalsAndKeepsLog() {
        User a = userService.register("A");
        User b = userService.register("B");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());
        gameService.startGame(a.getId(), room.getRoomCode());

        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        ScoreEntry entry = gameService.submitScore(a.getId(), room.getRoomCode(), bP.getId(), 8, "");

        ScoreEntry revertLog = gameService.revertScore(a.getId(), room.getRoomCode(), entry.getId());

        RoomPlayer aP = playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get();
        bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        assertThat(aP.getTotalScore()).isZero();
        assertThat(bP.getTotalScore()).isZero();
        assertThat(entryRepository.findById(entry.getId()).get().getReverted()).isTrue();
        assertThat(revertLog.getType()).isEqualTo("SCORE_REVERT");
        assertThat(revertLog.getRevertOfEntryId()).isEqualTo(entry.getId());
    }

    @Test
    void scoreCannotBeRevertedAfterOneMinute() {
        User a = userService.register("A");
        User b = userService.register("B");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());
        gameService.startGame(a.getId(), room.getRoomCode());

        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        ScoreEntry entry = gameService.submitScore(a.getId(), room.getRoomCode(), bP.getId(), 8, "");
        entry.setCreatedAt(LocalDateTime.now().minusSeconds(61));
        entryRepository.save(entry);

        assertThatThrownBy(() -> gameService.revertScore(a.getId(), room.getRoomCode(), entry.getId()))
            .hasMessageContaining("一分钟");
    }

    @Test
    void endGameCreatesMinimalSettlementTransfersWithAaRoomFee() {
        User a = userService.register("A");
        User b = userService.register("B");
        User c = userService.register("C");
        Room room = roomService.createRoom(a.getId(), 30);
        roomService.joinRoom(b.getId(), room.getRoomCode());
        roomService.joinRoom(c.getId(), room.getRoomCode());
        gameService.startGame(a.getId(), room.getRoomCode());

        RoomPlayer aP = playerRepository.findByRoomIdAndUserId(room.getId(), a.getId()).get();
        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();
        RoomPlayer cP = playerRepository.findByRoomIdAndUserId(room.getId(), c.getId()).get();
        gameService.submitScore(b.getId(), room.getRoomCode(), aP.getId(), 20, "");
        gameService.submitScore(c.getId(), room.getRoomCode(), aP.getId(), 20, "");

        gameService.endGame(a.getId(), room.getRoomCode());

        List<SettlementTransfer> transfers = gameService.getSettlementTransfers(room.getId());
        assertThat(transfers).hasSize(2);
        assertThat(transfers).extracting(SettlementTransfer::getAmount).containsExactly(30, 30);
        assertThat(transfers).allMatch(t -> t.getToPlayerId().equals(aP.getId()));
    }

    @Test
    void nonHostCanPayRoomFeeAndReceiveAaSettlement() {
        User a = userService.register("A");
        User b = userService.register("B");
        User c = userService.register("C");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());
        roomService.joinRoom(c.getId(), room.getRoomCode());
        gameService.setRoomFee(b.getId(), room.getRoomCode(), 30);
        gameService.startGame(a.getId(), room.getRoomCode());

        RoomPlayer bP = playerRepository.findByRoomIdAndUserId(room.getId(), b.getId()).get();

        gameService.endGame(a.getId(), room.getRoomCode());

        List<SettlementTransfer> transfers = gameService.getSettlementTransfers(room.getId());
        assertThat(transfers).hasSize(2);
        assertThat(transfers).extracting(SettlementTransfer::getAmount).containsExactly(10, 10);
        assertThat(transfers).allMatch(t -> t.getToPlayerId().equals(bP.getId()));
    }

    @Test
    void setRoomFeeWritesLogWithoutChangingScores() {
        User a = userService.register("A");
        User b = userService.register("B");
        Room room = roomService.createRoom(a.getId());
        roomService.joinRoom(b.getId(), room.getRoomCode());

        ScoreEntry feeLog = gameService.setRoomFee(a.getId(), room.getRoomCode(), 24);

        assertThat(feeLog.getType()).isEqualTo("ROOM_FEE");
        assertThat(feeLog.getScore()).isEqualTo(24);
        assertThat(roomService.findByCode(room.getRoomCode()).getFeePayerId()).isEqualTo(a.getId());
        assertThat(playerRepository.findByRoomId(room.getId()))
            .extracting(RoomPlayer::getTotalScore)
            .containsOnly(0);
    }
}
