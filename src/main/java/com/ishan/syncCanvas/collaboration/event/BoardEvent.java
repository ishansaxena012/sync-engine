package com.ishan.syncCanvas.collaboration.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** NORMAL_OPERATION, UNDO_OPERATION, or REDO_OPERATION — see {@link EventKind}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_kind", nullable = false, updatable = false, length = 20)
    private EventKind eventKind;

    /**
     * For an UNDO_OPERATION, the event it undoes; for a REDO_OPERATION, the
     * UNDO_OPERATION it redoes. Null for a NORMAL_OPERATION.
     */
    @Column(name = "source_event_id", updatable = false)
    private UUID sourceEventId;

    public static BoardEvent of(UUID boardId, long sequence, UUID operationId, UUID userId,
                                String operationType, String payload) {
        return of(boardId, sequence, operationId, userId, operationType, payload,
                EventKind.NORMAL_OPERATION, null);
    }

    public static BoardEvent of(UUID boardId, long sequence, UUID operationId, UUID userId,
                                String operationType, String payload, EventKind eventKind, UUID sourceEventId) {
        BoardEvent event = new BoardEvent();
        event.id = UUID.randomUUID();
        event.boardId = boardId;
        event.sequence = sequence;
        event.operationId = operationId;
        event.userId = userId;
        event.operationType = operationType;
        event.payload = payload;
        event.createdAt = Instant.now();
        event.eventKind = eventKind;
        event.sourceEventId = sourceEventId;
        return event;
    }
}
