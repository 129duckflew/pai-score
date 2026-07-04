package com.example.service;

import com.example.entity.Room;
import com.example.entity.RoomPlayer;
import com.example.entity.ScoreEntry;
import com.example.repository.RoomPlayerRepository;
import com.example.repository.RoomRepository;
import com.example.repository.ScoreEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class GameService {

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository playerRepository;
    private final ScoreEntryRepository entryRepository;
    private final UserService userService;
    private final Random random = new Random();

    public GameService(RoomRepository roomRepository, RoomPlayerRepository playerRepository,
                       ScoreEntryRepository entryRepository, UserService userService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.entryRepository = entryRepository;
        this.userService = userService;
    }

    @Transactional
    public Room startGame(Long userId, String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!room.getHostId().equals(userId)) {
            throw new RuntimeException("仅房主可以开始游戏");
        }
        if (!"WAITING".equals(room.getStatus())) {
            throw new RuntimeException("游戏已开始");
        }
        if (playerRepository.countByRoomId(room.getId()) < 2) {
            throw new RuntimeException("至少需要2名玩家");
        }

        room.setStatus("PLAYING");
        return roomRepository.save(room);
    }

    @Transactional
    public ScoreEntry submitScore(Long userId, String roomCode, Long targetPlayerId, Integer score, String note) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!"PLAYING".equals(room.getStatus())) {
            throw new RuntimeException("游戏未进行中");
        }

        RoomPlayer self = playerRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new RuntimeException("玩家不在此房间"));

        if (self.getId().equals(targetPlayerId)) {
            throw new RuntimeException("不能对自己记分");
        }

        RoomPlayer target = playerRepository.findById(targetPlayerId)
                .orElseThrow(() -> new RuntimeException("玩家不存在"));

        if (!target.getRoomId().equals(room.getId())) {
            throw new RuntimeException("玩家不在此房间");
        }

        ScoreEntry entry = new ScoreEntry();
        entry.setRoomId(room.getId());
        entry.setSourcePlayerId(self.getId());
        entry.setTargetPlayerId(targetPlayerId);
        entry.setAddedByUserId(userId);
        entry.setScore(score);
        entry.setNote(note);
        entry.setCreatedAt(LocalDateTime.now());
        entry = entryRepository.save(entry);

        self.setTotalScore(self.getTotalScore() - score);
        target.setTotalScore(target.getTotalScore() + score);
        playerRepository.save(self);
        playerRepository.save(target);

        return entry;
    }

    @Transactional
    public Room endGame(Long userId, String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!room.getHostId().equals(userId)) {
            throw new RuntimeException("仅房主可以结束游戏");
        }
        if (!"PLAYING".equals(room.getStatus())) {
            throw new RuntimeException("游戏未进行中");
        }

        room.setStatus("FINISHED");
        return roomRepository.save(room);
    }

    @Transactional
    public ScoreEntry rollDice(Long userId, String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!"PLAYING".equals(room.getStatus())) {
            throw new RuntimeException("游戏未进行中");
        }

        RoomPlayer player = playerRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new RuntimeException("玩家不在此房间"));

        int d1 = random.nextInt(6) + 1;
        int d2 = random.nextInt(6) + 1;
        int total = d1 + d2;

        ScoreEntry entry = new ScoreEntry();
        entry.setRoomId(room.getId());
        entry.setSourcePlayerId(player.getId());
        entry.setTargetPlayerId(player.getId());
        entry.setAddedByUserId(userId);
        entry.setScore(total);
        entry.setNote("🎲" + d1 + " 🎲" + d2);
        entry.setType("DICE_ROLL");
        entry.setCreatedAt(LocalDateTime.now());
        return entryRepository.save(entry);
    }

    public List<ScoreEntry> getEntries(Long roomId) {
        return entryRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
    }
}
