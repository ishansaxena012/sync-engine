package com.ishan.syncCanvas.collaboration.undo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardUndoStackEntryRepository extends JpaRepository<BoardUndoStackEntry, UUID> {

    Optional<BoardUndoStackEntry> findByBoardIdAndUserIdAndStackPosition(
            UUID boardId, UUID userId, long stackPosition);

    /** Clears an abandoned redo branch before a new action is appended — see {@code UndoHistoryService}. */
    @Modifying
    @Query("DELETE FROM BoardUndoStackEntry e WHERE e.boardId = :boardId AND e.userId = :userId AND e.stackPosition > :position")
    void deleteAboveStackPosition(@Param("boardId") UUID boardId, @Param("userId") UUID userId, @Param("position") long position);

    void deleteByBoardId(UUID boardId);
}
