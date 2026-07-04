package com.example.controller;

import com.example.dto.RegisterRequest;
import com.example.dto.RoomHistoryResponse;
import com.example.dto.RoomSummary;
import com.example.dto.UserResponse;
import com.example.entity.*;
import com.example.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final RoomService roomService;
    private final GameService gameService;

    public UserController(UserService userService, RoomService roomService, GameService gameService) {
        this.userService = userService;
        this.roomService = roomService;
        this.gameService = gameService;
    }

    @PostMapping("/users/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        User user = userService.register(req.getUsername().trim());
        return ResponseEntity.ok(new UserResponse(user.getId(), user.getUsername(), user.getToken(), user.getAvatar()));
    }

    @GetMapping("/users/{userId}/history")
    public ResponseEntity<List<RoomSummary>> getUserHistory(@PathVariable Long userId) {
        User user = userService.findById(userId);
        if (user == null) return ResponseEntity.notFound().build();

        List<Room> rooms = roomService.getUserRooms(userId);
        List<RoomSummary> summaries = rooms.stream()
            .map(r -> {
                long count = roomService.getPlayers(r.getId()).size();
                return new RoomSummary(r.getRoomCode(), r.getStatus(),
                    (int) count, r.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            })
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/rooms/{roomCode}/history")
    public ResponseEntity<RoomHistoryResponse> getRoomHistory(@PathVariable String roomCode) {
        Room room = roomService.findByCode(roomCode);
        if (room == null) return ResponseEntity.notFound().build();

        RoomHistoryResponse res = new RoomHistoryResponse();
        res.setRoomCode(room.getRoomCode());
        res.setStatus(room.getStatus());

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
            RoomPlayer source = players.stream()
                .filter(p -> p.getId().equals(e.getSourcePlayerId())).findFirst().orElse(null);
            ei.setSourcePlayerName(source != null ?
                playerInfos.stream().filter(pi -> pi.getPlayerId().equals(source.getId()))
                    .findFirst().map(RoomHistoryResponse.PlayerInfo::getUsername).orElse("?") : "?");
            RoomPlayer target = players.stream()
                .filter(p -> p.getId().equals(e.getTargetPlayerId())).findFirst().orElse(null);
            ei.setTargetPlayerName(target != null ?
                playerInfos.stream().filter(pi -> pi.getPlayerId().equals(target.getId()))
                    .findFirst().map(RoomHistoryResponse.PlayerInfo::getUsername).orElse("?") : "?");
            User adder = userService.findById(e.getAddedByUserId());
            ei.setAddedByUsername(adder != null ? adder.getUsername() : "?");
            return ei;
        }).collect(Collectors.toList());
        res.setEntries(entryInfos);

        return ResponseEntity.ok(res);
    }
}
