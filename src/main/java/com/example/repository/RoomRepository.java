package com.example.repository;

import com.example.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);
    List<Room> findByStatus(String status);
    Page<Room> findByStatus(String status, Pageable pageable);
    Page<Room> findByRoomCodeContainingIgnoreCaseOrNameContainingIgnoreCase(String roomCode, String name, Pageable pageable);
    Page<Room> findByStatusAndRoomCodeContainingIgnoreCaseOrStatusAndNameContainingIgnoreCase(
            String statusForCode, String roomCode, String statusForName, String name, Pageable pageable);
}
