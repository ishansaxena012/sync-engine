package com.ishan.syncCanvas.collaboration.undo;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ishan.syncCanvas.collaboration.event.BoardEvent;
import com.ishan.syncCanvas.collaboration.event.BoardEventService;
import com.ishan.syncCanvas.collaboration.event.EventKind;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The transactional core of undo/redo: locks the acting user's undo cursor for this
 * board, verifies no conflicting change has landed since the entry being toggled last
 * settled, applies the in-memory mutation, and commits it as a new durable event with
 * the stack entry and cursor updated to match — all one PostgreSQL transaction. A
 * separate bean (not a private method on {@code UndoRedoService}) so the
 * {@code @Transactional} proxy actually intercepts the call; see {@code UndoRedoService}.
 *
 * <p>The cursor row lock is acquired before the in-memory apply and held for the whole
 * method, which is what makes this safe across concurrent requests for the same
 * user+board on different instances: a second request blocks on the same row until this
 * one commits, and by then the cursor it reads has already moved past the entry this one
 * touched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UndoRedoTransactionService {

    private final BoardUndoCursorRepository cursorRepository;
    private final BoardUndoStackEntryRepository stackEntryRepository;
    private final OperationProcessor operationProcessor;
    private final BoardEventService boardEventService;
    private final DirtySessionTracker dirtySessionTracker;

    @Transactional
    public Outcome applyUndo(BoardSession session, UUID boardId, UUID userId) {
        BoardUndoCursor cursor = cursorRepository.findByIdForUpdate(boardId, userId).orElse(null);
        if (cursor == null || cursor.getTopPosition() == 0) {
            return Outcome.of(UndoRedoResult.nothingToUndo(), null);
        }

        BoardUndoStackEntry entry = stackEntryRepository
                .findByBoardIdAndUserIdAndStackPosition(boardId, userId, cursor.getTopPosition())
                .orElseThrow(() -> new IllegalStateException(
                        "Undo stack entry missing at position " + cursor.getTopPosition()
                                + " for board " + boardId + " user " + userId));

        Map<UUID, Long> live = UndoOperationFactory.currentVersions(session, entry.getChange().affectedObjectIds());
        if (!live.equals(entry.getExpectedVersions())) {
            return Outcome.of(UndoRedoResult.conflict(entry.getLastEventSequence()), null);
        }

        Operation inverse = UndoOperationFactory.buildUndoOperation(entry.getChange(), boardId, userId, live);
        operationProcessor.apply(inverse, session, UndoOperationFactory.applyModeFor(inverse));
        dirtySessionTracker.markDirty(boardId);

        BoardEvent event = boardEventService.commitUndoRedoEvent(
                boardId, inverse, userId, EventKind.UNDO_OPERATION, entry.getLastEventId());

        Map<UUID, Long> settled = UndoOperationFactory.currentVersions(session, entry.getChange().affectedObjectIds());
        long sourceSequence = entry.getLastEventSequence();
        entry.settle(UndoStackStatus.UNDONE, event.getId(), event.getSequence(), settled);
        stackEntryRepository.save(entry);

        cursor.setTopPosition(cursor.getTopPosition() - 1);
        cursor.touch();
        cursorRepository.save(cursor);

        return Outcome.of(UndoRedoResult.ok(sourceSequence, event.getSequence()),
                new SequencedOperation(event.getSequence(), inverse));
    }

    @Transactional
    public Outcome applyRedo(BoardSession session, UUID boardId, UUID userId) {
        BoardUndoCursor cursor = cursorRepository.findByIdForUpdate(boardId, userId).orElse(null);
        if (cursor == null || cursor.getTopPosition() >= cursor.getMaxPosition()) {
            return Outcome.of(UndoRedoResult.nothingToRedo(), null);
        }

        long targetPosition = cursor.getTopPosition() + 1;
        BoardUndoStackEntry entry = stackEntryRepository
                .findByBoardIdAndUserIdAndStackPosition(boardId, userId, targetPosition)
                .orElseThrow(() -> new IllegalStateException(
                        "Undo stack entry missing at position " + targetPosition
                                + " for board " + boardId + " user " + userId));

        Map<UUID, Long> live = UndoOperationFactory.currentVersions(session, entry.getChange().affectedObjectIds());
        if (!live.equals(entry.getExpectedVersions())) {
            return Outcome.of(UndoRedoResult.conflict(entry.getLastEventSequence()), null);
        }

        Operation forward = UndoOperationFactory.buildRedoOperation(entry.getChange(), boardId, userId, live);
        operationProcessor.apply(forward, session, UndoOperationFactory.applyModeFor(forward));
        dirtySessionTracker.markDirty(boardId);

        BoardEvent event = boardEventService.commitUndoRedoEvent(
                boardId, forward, userId, EventKind.REDO_OPERATION, entry.getLastEventId());

        Map<UUID, Long> settled = UndoOperationFactory.currentVersions(session, entry.getChange().affectedObjectIds());
        long sourceSequence = entry.getLastEventSequence();
        entry.settle(UndoStackStatus.ACTIVE, event.getId(), event.getSequence(), settled);
        stackEntryRepository.save(entry);

        cursor.setTopPosition(targetPosition);
        cursor.touch();
        cursorRepository.save(cursor);

        return Outcome.of(UndoRedoResult.ok(sourceSequence, event.getSequence()),
                new SequencedOperation(event.getSequence(), forward));
    }

    /** The client-facing result, plus (only on success) the operation to distribute. */
    public record Outcome(UndoRedoResult result, SequencedOperation toDistribute) {
        public static Outcome of(UndoRedoResult result, SequencedOperation toDistribute) {
            return new Outcome(result, toDistribute);
        }
    }
}
