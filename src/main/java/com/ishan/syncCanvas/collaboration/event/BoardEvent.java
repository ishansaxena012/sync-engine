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
 * The durable record of one accepted operation — "what happened to the board", as
 * opposed to {@code CanvasObject}, which is "what the board looks like now". Immutable
 * once written; every field is server-assigned.
 */
@Entity
@Table(name = "board_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_board_event_board_sequence", columnNames = {"board_id", "sequence"}),
        @UniqueConstraint(name = "uk_board_event_board_operation", columnNames = {"board_id", "operation_id"})
})
@Getter
@NoArgsConstructor
public class BoardEvent {

    @Id
    private UUID id;

    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    @Column(nullable = false, updatable = false)
    private long sequence;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "operation_type", nullable = false, updatable = false, length = 50)
    private String operationType;

    /** The full serialized {@code Operation}, enough to re-apply it exactly during reconstruction. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static BoardEvent of(UUID boardId, long sequence, UUID operationId, UUID userId,
                                String operationType, String payload) {
        BoardEvent event = new BoardEvent();
        event.id = UUID.randomUUID();
        event.boardId = boardId;
        event.sequence = sequence;
        event.operationId = operationId;
        event.userId = userId;
        event.operationType = operationType;
        event.payload = payload;
        event.createdAt = Instant.now();
        return event;
    }
}
