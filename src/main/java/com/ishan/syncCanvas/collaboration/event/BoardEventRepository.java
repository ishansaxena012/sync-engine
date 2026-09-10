package com.ishan.syncCanvas.collaboration.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal-only access to the durable event log for synchronization and
 * reconstruction. Every range query is ordered by sequence and either bounded by a
 * {@link Pageable} or by an explicit upper sequence — nothing here loads a whole
 * board's history unbounded, and nothing here is exposed over HTTP.
 */
public interface BoardEventRepository extends JpaRepository<BoardEvent, UUID> {

    List<BoardEvent> findByBoardIdAndSequenceGreaterThanOrderBySequenceAsc(
            UUID boardId, long sequence, Pageable pageable);

    List<BoardEvent> findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
            UUID boardId, long fromExclusive, long toInclusive);

    Optional<BoardEvent> findTopByBoardIdOrderBySequenceDesc(UUID boardId);

    Optional<BoardEvent> findTopByBoardIdOrderBySequenceAsc(UUID boardId);

    void deleteByBoardId(UUID boardId);
}
