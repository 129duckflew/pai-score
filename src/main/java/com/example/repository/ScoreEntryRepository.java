package com.example.repository;

import com.example.entity.ScoreEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreEntryRepository extends JpaRepository<ScoreEntry, Long> {
    List<ScoreEntry> findByRoomIdOrderByCreatedAtAsc(Long roomId);
}
