package com.example.controller;

import com.example.dto.RoomHistoryResponse;
import com.example.entity.Room;
import com.example.entity.RoomPlayer;
import com.example.entity.ScoreEntry;
import com.example.entity.User;
import com.example.service.AdminAuthService;
import com.example.service.AdminLogBuffer;
import com.example.service.GameService;
import com.example.service.OnlineUserRegistry;
import com.example.service.RoomService;
import com.example.service.UserService;
import com.example.websocket.SocketIOEventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAuthService adminAuthService;
    private final UserService userService;
    private final RoomService roomService;
    private final GameService gameService;
    private final OnlineUserRegistry onlineUserRegistry;
    private final AdminLogBuffer adminLogBuffer;
    private final ObjectProvider<SocketIOEventListener> socketListeners;
    private final Instant startedAt = Instant.now();

    public AdminController(AdminAuthService adminAuthService, UserService userService,
                           RoomService roomService, GameService gameService,
                           OnlineUserRegistry onlineUserRegistry, AdminLogBuffer adminLogBuffer,
                           ObjectProvider<SocketIOEventListener> socketListeners) {
        this.adminAuthService = adminAuthService;
        this.userService = userService;
        this.roomService = roomService;
        this.gameService = gameService;
        this.onlineUserRegistry = onlineUserRegistry;
        this.adminLogBuffer = adminLogBuffer;
        this.socketListeners = socketListeners;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {
        try {
            String token = adminAuthService.login(request.password());
            return ResponseEntity.ok(Map.of("token", token));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> users(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(defaultValue = "") String q) {
        if (!authorized(authorization)) return unauthorized();
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = userService.listUsers(q, pageable);
        return ResponseEntity.ok(pageResponse(users.map(this::userToMap)));
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestBody UserRequest request) {
        if (!authorized(authorization)) return unauthorized();
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名不能为空"));
        }
        User user = userService.createUser(request.username(), request.avatar());
        return ResponseEntity.ok(userToMap(user));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id,
                                        @RequestBody UserRequest request) {
        if (!authorized(authorization)) return unauthorized();
        User user = userService.updateUser(id, request.username(), request.avatar(), request.token(), request.activeRoomCode());
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(userToMap(user));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id) {
        if (!authorized(authorization)) return unauthorized();
        if (userService.findById(id) == null) return ResponseEntity.notFound().build();
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms")
    public ResponseEntity<?> rooms(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(defaultValue = "") String q,
                                   @RequestParam(defaultValue = "") String status) {
        if (!authorized(authorization)) return unauthorized();
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Room> rooms = roomService.getRooms(q, status, pageable);
        return ResponseEntity.ok(pageResponse(rooms.map(this::roomToMap)));
    }

    @GetMapping("/rooms/{roomCode}/history")
    public ResponseEntity<?> roomHistory(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable String roomCode) {
        if (!authorized(authorization)) return unauthorized();
        Room room = roomService.findByCode(roomCode);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(buildRoomHistory(room));
    }

    @PostMapping("/rooms/close-all")
    public ResponseEntity<?> closeAllRooms(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!authorized(authorization)) return unauthorized();
        List<String> closedRoomCodes = roomService.closeAllActiveRooms();
        SocketIOEventListener listener = socketListeners.getIfAvailable();
        if (listener != null) {
            listener.broadcastRoomsDestroyed(closedRoomCodes);
        }
        return ResponseEntity.ok(Map.of("closed", closedRoomCodes.size(), "roomCodes", closedRoomCodes));
    }

    @GetMapping("/runtime")
    public ResponseEntity<?> runtime(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!authorized(authorization)) return unauthorized();
        Runtime runtime = Runtime.getRuntime();
        java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        java.lang.management.MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        Map<String, Object> data = new HashMap<>();
        data.put("startedAt", startedAt.toString());
        data.put("uptimeSeconds", Duration.between(startedAt, Instant.now()).toSeconds());
        data.put("availableProcessors", runtime.availableProcessors());
        data.put("threadCount", threads.getThreadCount());
        data.put("heapUsedBytes", memory.getHeapMemoryUsage().getUsed());
        data.put("heapMaxBytes", memory.getHeapMemoryUsage().getMax());
        data.put("nonHeapUsedBytes", memory.getNonHeapMemoryUsage().getUsed());
        data.put("systemLoadAverage", os.getSystemLoadAverage());
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            data.put("processCpuLoad", sunOs.getProcessCpuLoad());
            data.put("systemCpuLoad", sunOs.getCpuLoad());
            data.put("committedVirtualMemoryBytes", sunOs.getCommittedVirtualMemorySize());
        }
        data.put("onlineUsers", onlineUserRegistry.onlineUserIds().size());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/logs")
    public ResponseEntity<?> logs(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @RequestParam(defaultValue = "200") int limit,
                                  @RequestParam(required = false) String traceId) {
        if (!authorized(authorization)) return unauthorized();
        List<AdminLogBuffer.LogEntry> entries = adminLogBuffer.byTraceId(traceId, limit);
        return ResponseEntity.ok(Map.of(
            "lines", entries.stream().map(AdminLogBuffer.LogEntry::line).toList(),
            "entries", entries
        ));
    }

    private boolean authorized(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        return adminAuthService.isValid(authorization.substring("Bearer ".length()));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "管理员未认证"));
    }

    private int safePage(int page) {
        return Math.max(0, page);
    }

    private int safeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private Map<String, Object> pageResponse(Page<?> page) {
        return Map.of(
            "content", page.getContent(),
            "page", page.getNumber(),
            "size", page.getSize(),
            "totalElements", page.getTotalElements(),
            "totalPages", page.getTotalPages()
        );
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("token", user.getToken());
        data.put("activeRoomCode", user.getActiveRoomCode());
        data.put("avatar", user.getAvatar());
        data.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        data.put("online", onlineUserRegistry.isOnline(user.getId()));
        return data;
    }

    private Map<String, Object> roomToMap(Room room) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", room.getId());
        data.put("roomCode", room.getRoomCode());
        data.put("name", room.getName());
        data.put("status", room.getStatus());
        data.put("hostId", room.getHostId());
        data.put("feeAmount", room.getFeeAmount() != null ? room.getFeeAmount() : 0);
        data.put("playerCount", roomService.getPlayers(room.getId()).size());
        data.put("createdAt", room.getCreatedAt() != null ? room.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        return data;
    }

    private RoomHistoryResponse buildRoomHistory(Room room) {
        RoomHistoryResponse res = new RoomHistoryResponse();
        res.setRoomCode(room.getRoomCode());
        res.setName(room.getName());
        res.setStatus(room.getStatus());
        res.setFeeAmount(room.getFeeAmount() != null ? room.getFeeAmount() : 0);
        Long feePayerId = room.getFeeAmount() != null && room.getFeeAmount() > 0
            ? (room.getFeePayerId() != null ? room.getFeePayerId() : room.getHostId())
            : null;
        res.setFeePayerId(feePayerId);
        User feePayer = feePayerId != null ? userService.findById(feePayerId) : null;
        res.setFeePayerName(feePayer != null ? feePayer.getUsername() : null);

        List<RoomPlayer> players = roomService.getPlayers(room.getId());
        List<RoomHistoryResponse.PlayerInfo> playerInfos = players.stream().map(p -> {
            RoomHistoryResponse.PlayerInfo pi = new RoomHistoryResponse.PlayerInfo();
            pi.setPlayerId(p.getId());
            pi.setUserId(p.getUserId());
            User u = userService.findById(p.getUserId());
            pi.setUsername(u != null ? u.getUsername() : "?");
            pi.setAvatar(u != null ? u.getAvatar() : null);
            pi.setTotalScore(p.getTotalScore());
            return pi;
        }).collect(Collectors.toList());
        res.setPlayers(playerInfos);

        Map<Long, Integer> roomFeeShares = gameService.calculateRoomFeeShares(room);
        res.setRoomFeeShares(players.stream().map(p -> {
            RoomHistoryResponse.RoomFeeShareInfo si = new RoomHistoryResponse.RoomFeeShareInfo();
            si.setPlayerId(p.getId());
            si.setUserId(p.getUserId());
            si.setPlayerName(playerInfos.stream().filter(pi -> pi.getPlayerId().equals(p.getId()))
                .findFirst().map(RoomHistoryResponse.PlayerInfo::getUsername).orElse("?"));
            si.setAmount(roomFeeShares.getOrDefault(p.getId(), 0));
            return si;
        }).collect(Collectors.toList()));

        List<ScoreEntry> allEntries = gameService.getEntries(room.getId());
        List<RoomHistoryResponse.EntryInfo> entryInfos = allEntries.stream().map(e -> {
            RoomHistoryResponse.EntryInfo ei = new RoomHistoryResponse.EntryInfo();
            ei.setId(e.getId());
            ei.setSourcePlayerId(e.getSourcePlayerId());
            ei.setTargetPlayerId(e.getTargetPlayerId());
            ei.setScore(e.getScore());
            ei.setType(e.getType());
            ei.setNote(e.getNote());
            ei.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            ei.setReverted(Boolean.TRUE.equals(e.getReverted()));
            ei.setRevertedAt(e.getRevertedAt() != null ? e.getRevertedAt().toString() : null);
            ei.setRevertedByUserId(e.getRevertedByUserId());
            ei.setRevertOfEntryId(e.getRevertOfEntryId());
            RoomPlayer source = players.stream().filter(p -> p.getId().equals(e.getSourcePlayerId())).findFirst().orElse(null);
            ei.setSourcePlayerName(source != null ? playerInfos.stream()
                .filter(pi -> pi.getPlayerId().equals(source.getId()))
                .findFirst().map(RoomHistoryResponse.PlayerInfo::getUsername).orElse("?") : "?");
            RoomPlayer target = players.stream().filter(p -> p.getId().equals(e.getTargetPlayerId())).findFirst().orElse(null);
            ei.setTargetPlayerName(target != null ? playerInfos.stream()
                .filter(pi -> pi.getPlayerId().equals(target.getId()))
                .findFirst().map(RoomHistoryResponse.PlayerInfo::getUsername).orElse("?") : "?");
            User adder = userService.findById(e.getAddedByUserId());
            ei.setAddedByUsername(adder != null ? adder.getUsername() : "?");
            return ei;
        }).collect(Collectors.toList());
        res.setEntries(entryInfos);
        res.setSettlementTransfers(gameService.getSettlementTransfers(room.getId()).stream().map(t -> {
            RoomHistoryResponse.SettlementTransferInfo ti = new RoomHistoryResponse.SettlementTransferInfo();
            ti.setFromPlayerId(t.getFromPlayerId());
            ti.setToPlayerId(t.getToPlayerId());
            ti.setAmount(t.getAmount());
            ti.setFromPlayerName(playerInfos.stream().filter(p -> p.getPlayerId().equals(t.getFromPlayerId()))
                .findFirst().map(RoomHistoryResponse.PlayerInfo::getUsername).orElse("?"));
            ti.setToPlayerName(playerInfos.stream().filter(p -> p.getPlayerId().equals(t.getToPlayerId()))
                .findFirst().map(RoomHistoryResponse.PlayerInfo::getUsername).orElse("?"));
            return ti;
        }).collect(Collectors.toList()));
        return res;
    }

    public record AdminLoginRequest(String password) {}
    public record UserRequest(String username, String avatar, String token, String activeRoomCode) {}
}
