package com.example.service;

import com.example.entity.Room;
import com.example.entity.RoomPlayer;
import com.example.entity.User;
import com.example.repository.RoomPlayerRepository;
import com.example.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository playerRepository;
    private final UserService userService;

    public RoomService(RoomRepository roomRepository, RoomPlayerRepository playerRepository, UserService userService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.userService = userService;
    }

    @Transactional
    public Room createRoom(Long hostUserId) {
        checkNoActiveRoom(hostUserId);
        User hostUser = userService.findById(hostUserId);
        if (hostUser == null) throw new RuntimeException("用户不存在");
        Room room = new Room();
        room.setRoomCode(generateRoomCode());
        room.setHostId(hostUserId);
        room.setName(hostUser.getUsername() + "的房间");
        room.setStatus("WAITING");
        room.setCreatedAt(LocalDateTime.now());
        room = roomRepository.save(room);

        RoomPlayer host = new RoomPlayer();
        host.setRoomId(room.getId());
        host.setUserId(hostUserId);
        host.setTotalScore(0);
        host.setJoinedAt(LocalDateTime.now());
        playerRepository.save(host);

        return room;
    }

    @Transactional
    public RoomPlayer joinRoom(Long userId, String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!"WAITING".equals(room.getStatus())) {
            throw new RuntimeException("游戏已开始，无法加入");
        }

        checkNoActiveRoom(userId);

        if (playerRepository.countByRoomId(room.getId()) >= 8) {
            throw new RuntimeException("房间已满（最多8人）");
        }

        if (playerRepository.findByRoomIdAndUserId(room.getId(), userId).isPresent()) {
            throw new RuntimeException("你已在房间中");
        }

        RoomPlayer player = new RoomPlayer();
        player.setRoomId(room.getId());
        player.setUserId(userId);
        player.setTotalScore(0);
        player.setJoinedAt(LocalDateTime.now());
        return playerRepository.save(player);
    }

    @Transactional
    public void leaveRoom(Long userId, String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        RoomPlayer player = playerRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new RuntimeException("你不在房间中"));

        if (room.getHostId().equals(userId)) {
            disbandRoom(room);
        } else {
            if (!"WAITING".equals(room.getStatus())) {
                clearActiveRoom(userId, roomCode);
                return;
            }

            playerRepository.delete(player);

            long count = playerRepository.countByRoomId(room.getId());
            if (count == 0) {
                disbandRoom(room);
            }
        }
    }

    public Room findByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode).orElse(null);
    }

    public Room findById(Long roomId) {
        return roomRepository.findById(roomId).orElse(null);
    }

    public RoomPlayer findPlayer(Long roomId, Long userId) {
        return playerRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
    }

    public List<RoomPlayer> getPlayers(Long roomId) {
        return playerRepository.findByRoomId(roomId);
    }

    public List<RoomPlayer> getPlayersOrdered(Long roomId) {
        return playerRepository.findByRoomIdOrderByJoinedAtAsc(roomId);
    }

    public List<Room> getActiveRooms() {
        return roomRepository.findByStatus("WAITING");
    }

    public Page<Room> getRooms(String query, String status, Pageable pageable) {
        boolean hasQuery = query != null && !query.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasQuery && hasStatus) {
            String q = query.trim();
            String s = status.trim();
            return roomRepository.findByStatusAndRoomCodeContainingIgnoreCaseOrStatusAndNameContainingIgnoreCase(s, q, s, q, pageable);
        }
        if (hasQuery) {
            String q = query.trim();
            return roomRepository.findByRoomCodeContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, pageable);
        }
        if (hasStatus) {
            return roomRepository.findByStatus(status.trim(), pageable);
        }
        return roomRepository.findAll(pageable);
    }

    public List<Room> getRoomsByStatus(String status) {
        return roomRepository.findByStatus(status);
    }

    public List<Room> getUserRooms(Long userId) {
        List<RoomPlayer> players = playerRepository.findByUserId(userId);
        return players.stream()
                .map(p -> roomRepository.findById(p.getRoomId()).orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<String> closeAllActiveRooms() {
        List<Room> activeRooms = roomRepository.findAll().stream()
                .filter(this::isActiveRoom)
                .collect(Collectors.toList());
        for (Room room : activeRooms) {
            disbandRoom(room);
        }
        return activeRooms.stream().map(Room::getRoomCode).collect(Collectors.toList());
    }

    private void checkNoActiveRoom(Long userId) {
        List<RoomPlayer> players = playerRepository.findByUserId(userId);
        boolean hasActiveRoom = players.stream().anyMatch(rp ->
            roomRepository.findById(rp.getRoomId())
                .map(r -> "WAITING".equals(r.getStatus()) || "PLAYING".equals(r.getStatus()))
                .orElse(false)
        );
        if (hasActiveRoom) {
            throw new RuntimeException("你已在其他房间中，无法创建或加入新房间");
        }
    }

    private void disbandRoom(Room room) {
        room.setStatus("DISBANDED");
        roomRepository.save(room);
        for (RoomPlayer p : playerRepository.findByRoomId(room.getId())) {
            clearActiveRoom(p.getUserId(), room.getRoomCode());
        }
    }

    private boolean isActiveRoom(Room room) {
        return "WAITING".equals(room.getStatus()) || "PLAYING".equals(room.getStatus());
    }

    private void clearActiveRoom(Long userId, String roomCode) {
        User user = userService.findById(userId);
        if (user != null && roomCode.equals(user.getActiveRoomCode())) {
            user.setActiveRoomCode(null);
            userService.updateUser(user);
        }
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            if (roomRepository.findByRoomCode(code.toString()).isEmpty()) {
                return code.toString();
            }
        }
        throw new RuntimeException("无法生成房间码，请重试");
    }
}
