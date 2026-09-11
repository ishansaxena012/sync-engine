package com.ishan.syncCanvas.collaboration.undo;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardUndoCursorRepository extends JpaRepository<BoardUndoCursor, BoardUndoCursor.Key> {

    /**
     * {@code SELECT ... FOR UPDATE}. Serializes every undo/redo for one user on one
     * board — across threads and across every instance sharing the database — for the
     * whole duration of the caller's transaction, so two concurrent undo (or redo)
     * requests can never toggle the same stack entry twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BoardUndoCursor c WHERE c.id.boardId = :boardId AND c.id.userId = :userId")
    Optional<BoardUndoCursor> findByIdForUpdate(@Param("boardId") UUID boardId, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM BoardUndoCursor c WHERE c.id.boardId = :boardId")
    void deleteByBoardId(@Param("boardId") UUID boardId);
}
