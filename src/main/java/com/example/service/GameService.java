package com.example.service;

import com.example.entity.Room;
import com.example.entity.RoomPlayer;
import com.example.entity.ScoreEntry;
import com.example.entity.SettlementTransfer;
import com.example.repository.RoomPlayerRepository;
import com.example.repository.RoomRepository;
import com.example.repository.ScoreEntryRepository;
import com.example.repository.SettlementTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class GameService {

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository playerRepository;
    private final ScoreEntryRepository entryRepository;
    private final SettlementTransferRepository settlementRepository;
    private final UserService userService;
    private final Random random = new Random();

    public GameService(RoomRepository roomRepository, RoomPlayerRepository playerRepository,
                       ScoreEntryRepository entryRepository, SettlementTransferRepository settlementRepository,
                       UserService userService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.entryRepository = entryRepository;
        this.settlementRepository = settlementRepository;
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
        room = roomRepository.save(room);
        calculateSettlementTransfers(room);
        return room;
    }

    @Transactional
    public ScoreEntry setRoomFee(Long userId, String roomCode, Integer feeAmount) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!room.getHostId().equals(userId)) {
            throw new RuntimeException("仅房主可以设置房费");
        }
        if ("FINISHED".equals(room.getStatus()) || "DISBANDED".equals(room.getStatus())) {
            throw new RuntimeException("房间已结束，无法设置房费");
        }
        if (feeAmount == null || feeAmount < 0) {
            throw new RuntimeException("房费不能为负数");
        }

        room.setFeeAmount(feeAmount);
        roomRepository.save(room);

        RoomPlayer host = playerRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new RuntimeException("玩家不在此房间"));
        ScoreEntry entry = new ScoreEntry();
        entry.setRoomId(room.getId());
        entry.setSourcePlayerId(host.getId());
        entry.setTargetPlayerId(host.getId());
        entry.setAddedByUserId(userId);
        entry.setScore(feeAmount);
        entry.setType("ROOM_FEE");
        entry.setNote("房费 AA 平摊");
        entry.setCreatedAt(LocalDateTime.now());
        return entryRepository.save(entry);
    }

    @Transactional
    public ScoreEntry revertScore(Long userId, String roomCode, Long entryId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        if (!"PLAYING".equals(room.getStatus())) {
            throw new RuntimeException("游戏未进行中");
        }

        ScoreEntry original = entryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("记分记录不存在"));
        if (!original.getRoomId().equals(room.getId())) {
            throw new RuntimeException("记分记录不属于此房间");
        }
        if (!"SCORE".equals(original.getType())) {
            throw new RuntimeException("只能撤回普通记分");
        }
        if (Boolean.TRUE.equals(original.getReverted())) {
            throw new RuntimeException("记分已撤回");
        }
        if (!original.getAddedByUserId().equals(userId)) {
            throw new RuntimeException("只能撤回自己的记分");
        }
        LocalDateTime now = LocalDateTime.now();
        if (Duration.between(original.getCreatedAt(), now).toSeconds() > 60) {
            throw new RuntimeException("只能在一分钟内撤回");
        }

        RoomPlayer source = playerRepository.findById(original.getSourcePlayerId())
                .orElseThrow(() -> new RuntimeException("玩家不存在"));
        RoomPlayer target = playerRepository.findById(original.getTargetPlayerId())
                .orElseThrow(() -> new RuntimeException("玩家不存在"));

        source.setTotalScore(source.getTotalScore() + original.getScore());
        target.setTotalScore(target.getTotalScore() - original.getScore());
        playerRepository.save(source);
        playerRepository.save(target);

        original.setReverted(true);
        original.setRevertedAt(now);
        original.setRevertedByUserId(userId);
        entryRepository.save(original);

        ScoreEntry log = new ScoreEntry();
        log.setRoomId(room.getId());
        log.setSourcePlayerId(original.getSourcePlayerId());
        log.setTargetPlayerId(original.getTargetPlayerId());
        log.setAddedByUserId(userId);
        log.setScore(original.getScore());
        log.setType("SCORE_REVERT");
        log.setNote("撤回记分 #" + original.getId());
        log.setRevertOfEntryId(original.getId());
        log.setCreatedAt(now);
        return entryRepository.save(log);
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

    public List<SettlementTransfer> getSettlementTransfers(Long roomId) {
        return settlementRepository.findByRoomIdOrderByIdAsc(roomId);
    }

    private void calculateSettlementTransfers(Room room) {
        settlementRepository.deleteByRoomId(room.getId());

        List<RoomPlayer> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId());
        if (players.isEmpty()) return;

        Map<Long, Integer> balances = new HashMap<>();
        for (RoomPlayer player : players) {
            balances.put(player.getId(), player.getTotalScore());
        }
        applyAaRoomFee(room, players, balances);

        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : balances.entrySet()) {
            if (entry.getValue() < 0) debtors.add(new Balance(entry.getKey(), -entry.getValue()));
            if (entry.getValue() > 0) creditors.add(new Balance(entry.getKey(), entry.getValue()));
        }
        debtors.sort(Comparator.comparingLong(b -> b.playerId));
        creditors.sort(Comparator.comparingLong(b -> b.playerId));

        int i = 0;
        int j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            Balance debtor = debtors.get(i);
            Balance creditor = creditors.get(j);
            int amount = Math.min(debtor.amount, creditor.amount);
            if (amount > 0) {
                SettlementTransfer transfer = new SettlementTransfer();
                transfer.setRoomId(room.getId());
                transfer.setFromPlayerId(debtor.playerId);
                transfer.setToPlayerId(creditor.playerId);
                transfer.setAmount(amount);
                settlementRepository.save(transfer);
            }
            debtor.amount -= amount;
            creditor.amount -= amount;
            if (debtor.amount == 0) i++;
            if (creditor.amount == 0) j++;
        }
    }

    private void applyAaRoomFee(Room room, List<RoomPlayer> players, Map<Long, Integer> balances) {
        int feeAmount = room.getFeeAmount() != null ? room.getFeeAmount() : 0;
        if (feeAmount <= 0) return;

        int baseShare = feeAmount / players.size();
        int remainder = feeAmount % players.size();
        for (int i = 0; i < players.size(); i++) {
            RoomPlayer player = players.get(i);
            int share = baseShare + (i < remainder ? 1 : 0);
            balances.compute(player.getId(), (ignored, balance) -> (balance == null ? 0 : balance) - share);
        }

        RoomPlayer host = players.stream()
                .filter(player -> player.getUserId().equals(room.getHostId()))
                .findFirst()
                .orElse(null);
        if (host != null) {
            balances.compute(host.getId(), (ignored, balance) -> (balance == null ? 0 : balance) + feeAmount);
        }
    }

    private static class Balance {
        private final Long playerId;
        private int amount;

        private Balance(Long playerId, int amount) {
            this.playerId = playerId;
            this.amount = amount;
        }
    }
}
