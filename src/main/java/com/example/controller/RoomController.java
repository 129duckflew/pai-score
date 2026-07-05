package com.example.controller;

import com.example.dto.RoomSummary;
import com.example.entity.Room;
import com.example.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/active")
    public ResponseEntity<List<RoomSummary>> getActiveRooms() {
        List<Room> rooms = roomService.getActiveRooms();
        List<RoomSummary> summaries = rooms.stream()
            .map(r -> {
                long count = roomService.getPlayers(r.getId()).size();
                return new RoomSummary(r.getRoomCode(), r.getName(), r.getStatus(),
                    (int) count, r.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            })
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }
}
