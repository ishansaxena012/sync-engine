package com.ishan.syncCanvas.collaboration.undo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One undoable action in a single user's per-board history: what it changed (immutable),
 * and the version(s) the affected object(s) must currently be at for the next toggle
 * (undo if {@code ACTIVE}, redo if {@code UNDONE}) to be conflict-free. That expectation
 * is updated after every successful toggle so repeated undo/redo of the same entry keeps
 * working — see {@code UndoRedoService} for how it's read and written.
 *
 * <p>A missing object is represented in {@code expectedVersions} as {@link #NOT_EXISTS},
 * for a create/delete pair's "currently deleted" state.
 */
@Entity
@Table(name = "board_undo_stack_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_board_undo_stack_entry_position", columnNames = {"board_id", "user_id", "stack_position"})
})
@Getter
@Setter
@NoArgsConstructor
public class BoardUndoStackEntry {

    public static final long NOT_EXISTS = -1L;

    @Id
    private UUID id;

    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "stack_position", nullable = false, updatable = false)
    private long stackPosition;

    /** The most recently committed event in this entry's chain — the next toggle's {@code sourceEventId}. */
    @Column(name = "last_event_id", nullable = false)
    private UUID lastEventId;

    /** That event's sequence — reported back to the client as {@code sourceSequence}. */
    @Column(name = "last_event_sequence", nullable = false)
    private long lastEventSequence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private UndoableChange change;

    /** Per-object version this entry's next toggle must find, or {@link #NOT_EXISTS}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_versions", nullable = false, columnDefinition = "jsonb")
    private Map<UUID, Long> expectedVersions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UndoStackStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BoardUndoStackEntry create(
            UUID boardId, UUID userId, long stackPosition, UUID forwardEventId, long forwardEventSequence,
            UndoableChange change, Map<UUID, Long> expectedVersions) {
        BoardUndoStackEntry entry = new BoardUndoStackEntry();
        entry.id = UUID.randomUUID();
        entry.boardId = boardId;
        entry.userId = userId;
        entry.stackPosition = stackPosition;
        entry.lastEventId = forwardEventId;
        entry.lastEventSequence = forwardEventSequence;
        entry.change = change;
        entry.expectedVersions = expectedVersions;
        entry.status = UndoStackStatus.ACTIVE;
        entry.createdAt = Instant.now();
        entry.updatedAt = entry.createdAt;
        return entry;
    }

    /** Records a successful toggle: the new settled state to expect next time, and which event produced it. */
    public void settle(UndoStackStatus newStatus, UUID newEventId, long newEventSequence, Map<UUID, Long> newExpectedVersions) {
        this.status = newStatus;
        this.lastEventId = newEventId;
        this.lastEventSequence = newEventSequence;
        this.expectedVersions = newExpectedVersions;
        this.updatedAt = Instant.now();
    }
}
