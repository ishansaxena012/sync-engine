package com.ishan.syncCanvas.collaboration.undo;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One user's undo/redo pointer for one board: how many of their
 * {@link BoardUndoStackEntry} rows (positions {@code 1..topPosition}) are currently
 * applied. Locked {@code FOR UPDATE} (see {@code BoardUndoCursorRepository}) before every
 * undo/redo so two concurrent requests for the same user+board — from any instance —
 * serialize on this single row instead of racing to toggle the same entry twice.
 */
@Entity
@Table(name = "board_undo_cursor")
@Getter
@Setter
@NoArgsConstructor
public class BoardUndoCursor {

    @EmbeddedId
    private Key id;

    @Column(name = "top_position", nullable = false)
    private long topPosition;

    @Column(name = "max_position", nullable = false)
    private long maxPosition;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BoardUndoCursor empty(UUID boardId, UUID userId) {
        BoardUndoCursor cursor = new BoardUndoCursor();
        cursor.id = new Key(boardId, userId);
        cursor.topPosition = 0;
        cursor.maxPosition = 0;
        cursor.updatedAt = Instant.now();
        return cursor;
    }

    public UUID getBoardId() {
        return id.boardId;
    }

    public UUID getUserId() {
        return id.userId;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "board_id")
        private UUID boardId;
        @Column(name = "user_id")
        private UUID userId;

        public Key(UUID boardId, UUID userId) {
            this.boardId = Objects.requireNonNull(boardId);
            this.userId = Objects.requireNonNull(userId);
        }
    }
}
