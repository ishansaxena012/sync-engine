package com.ishan.syncCanvas.collaboration.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BoardSnapshotRepository extends JpaRepository<BoardSnapshot, UUID> {

    /** The newest snapshot at or before the given sequence — the starting point for reconstruction. */
    Optional<BoardSnapshot> findTopByBoardIdAndSequenceLessThanEqualOrderBySequenceDesc(UUID boardId, long sequence);

    boolean existsByBoardIdAndSequence(UUID boardId, long sequence);

    void deleteByBoardId(UUID boardId);
}
