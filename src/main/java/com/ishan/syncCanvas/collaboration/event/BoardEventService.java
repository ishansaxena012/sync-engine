package com.ishan.syncCanvas.collaboration.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.canvas.repository.CanvasObjectRepository;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import com.ishan.syncCanvas.collaboration.undo.UndoHistoryService;
import com.ishan.syncCanvas.collaboration.undo.UndoableChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The one place a durable sequence is allocated. {@link #commitEvent} is the
 * transactional core of Phase 8: it locks the board row, increments the board's
 * sequence, and inserts the event — so a rollback undoes all three together and a
 * committed operation is always exactly one event with exactly one sequence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardEventService {

    private final BoardRepository boardRepository;
    private final BoardEventRepository eventRepository;
    private final CanvasObjectRepository canvasObjectRepository;
    private final BoardSnapshotService snapshotService;
    private final OperationSequenceService operationSequenceService;
    private final UndoHistoryService undoHistoryService;
    private final ObjectMapper objectMapper;

    /**
     * Allocates the next sequence for the board and records the event, atomically. Also
     * appends the change to the author's undo stack, in the same transaction, if one is
     * given — every current client operation type produces one; {@code null} is only for
     * callers (tests, or any future non-undoable operation type) that have none.
     * Redis pub/sub and the replay buffer are deliberately NOT part of this transaction —
     * they run after commit and are best-effort.
     *
     * @return the saved event, carrying the committed sequence and its own id (the latter
     *         needed by undo/redo history to link a future undo back to this event)
     */
    @Transactional
    public BoardEvent commitEvent(UUID boardId, Operation operation, UUID userId, UndoableChange change) {
        Board board = boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BoardAccessDeniedException("Board not found: " + boardId));

        if (board.getSequence() == 0) {
            bootstrapLegacyBoard(board);
        }

        long sequence = board.getSequence() + 1;
        board.setSequence(sequence);
        boardRepository.save(board);

        BoardEvent event = eventRepository.save(BoardEvent.of(
                boardId,
                sequence,
                operation.operationId(),
                userId,
                operation.type().name(),
                serialize(operation)));

        if (change != null) {
            undoHistoryService.recordNormalOperation(boardId, userId, event, change);
        }

        return event;
    }

    /**
     * Same row-locked sequence allocation as {@link #commitEvent}, for an event produced
     * by an undo or redo rather than a client-submitted operation. Kept separate rather
     * than overloading {@code commitEvent} so the ordinary path's signature — and every
     * existing caller and test of it — is untouched.
     *
     * @return the saved event
     */
    @Transactional
    public BoardEvent commitUndoRedoEvent(
            UUID boardId, Operation operation, UUID userId, EventKind kind, UUID sourceEventId) {
        Board board = boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BoardAccessDeniedException("Board not found: " + boardId));

        long sequence = board.getSequence() + 1;
        board.setSequence(sequence);
        boardRepository.save(board);

        return eventRepository.save(BoardEvent.of(
                boardId,
                sequence,
                operation.operationId(),
                userId,
                operation.type().name(),
                serialize(operation),
                kind,
                sourceEventId));
    }

    /**
     * Authoritative current sequence. PostgreSQL wins, but until a pre-Phase-8 board has
     * had its first durable commit its Redis counter may still be ahead, so take the max —
     * otherwise a reconnecting client of such a board could be told it's up to date while
     * Redis still holds operations it never received.
     */
    public long getLatestSequence(UUID boardId) {
        long durable = boardRepository.findById(boardId).map(Board::getSequence).orElse(0L);
        return Math.max(durable, operationSequenceService.currentSequence(boardId));
    }

    /**
     * First durable commit for a board that pre-dates Phase 8. Seeds the PostgreSQL
     * sequence from the Phase-7 Redis counter so numbering stays continuous for clients
     * already holding a lastSequenceReceived, and baselines a snapshot of the current
     * persisted state at that sequence so the board is reconstructible even though its
     * earlier history was never recorded as events. A genuinely new board (no Redis
     * history, no objects) skips this and starts at 1 from an empty state.
     */
    private void bootstrapLegacyBoard(Board board) {
        long redisSequence = operationSequenceService.currentSequence(board.getId());
        List<CanvasObject> existing = canvasObjectRepository.findByBoardId(board.getId());

        if (redisSequence == 0 && existing.isEmpty()) {
            return;
        }

        board.setSequence(redisSequence);
        snapshotService.saveSnapshot(board.getId(), redisSequence, existing);
        log.info("Bootstrapped board {} into the event store at sequence {} with {} objects",
                board.getId(), redisSequence, existing.size());
    }

    private String serialize(Operation operation) {
        try {
            return objectMapper.writeValueAsString(operation);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize operation " + operation.operationId(), ex);
        }
    }
}
