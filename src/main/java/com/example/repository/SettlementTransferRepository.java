package com.example.repository;

import com.example.entity.SettlementTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementTransferRepository extends JpaRepository<SettlementTransfer, Long> {
    List<SettlementTransfer> findByRoomIdOrderByIdAsc(Long roomId);
    void deleteByRoomId(Long roomId);
}
