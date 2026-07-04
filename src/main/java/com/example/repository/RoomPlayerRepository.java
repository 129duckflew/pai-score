package com.example.repository;

import com.example.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoomId(Long roomId);
    Optional<RoomPlayer> findByRoomIdAndUserId(Long roomId, Long userId);
    long countByRoomId(Long roomId);
    List<RoomPlayer> findByUserId(Long userId);
}
