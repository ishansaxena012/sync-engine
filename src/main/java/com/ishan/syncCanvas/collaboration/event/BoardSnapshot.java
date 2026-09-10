package com.ishan.syncCanvas.collaboration.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Complete board state immediately after operation {@code sequence} — never including
 * {@code sequence + 1} or later. Built only from durable inputs (an earlier snapshot plus
 * committed events), never from the live in-memory session, so it is exact and
 * deterministic across instances.
 */
@Entity
@Table(name = "board_snapshot", uniqueConstraints = {
        @UniqueConstraint(name = "uk_board_snapshot_board_sequence", columnNames = {"board_id", "sequence"})
})
@Getter
@NoArgsConstructor
public class BoardSnapshot {

    @Id
    private UUID id;

    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    @Column(nullable = false, updatable = false)
    private long sequence;

    /** Serialized list of {@code CanvasObject}s — the same canonical representation the current-state table uses. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static BoardSnapshot of(UUID boardId, long sequence, String state) {
        BoardSnapshot snapshot = new BoardSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.boardId = boardId;
        snapshot.sequence = sequence;
        snapshot.state = state;
        snapshot.createdAt = Instant.now();
        return snapshot;
    }
}
