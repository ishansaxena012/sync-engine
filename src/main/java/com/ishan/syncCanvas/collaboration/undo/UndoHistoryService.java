package com.ishan.syncCanvas.collaboration.undo;

import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ishan.syncCanvas.collaboration.event.BoardEvent;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Appends a newly-committed client operation to its author's per-board undo stack.
 * Called from {@code BoardEventService.commitEvent}, inside the same transaction as the
 * event and sequence it's recording — so a normal operation's durable event and its undo
 * history entry are always both there, or (on rollback) neither is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UndoHistoryService {

    private final BoardUndoCursorRepository cursorRepository;
    private final BoardUndoStackEntryRepository stackEntryRepository;
    private final BoardSessionManager sessionManager;

    @Transactional
    public void recordNormalOperation(UUID boardId, UUID userId, BoardEvent event, UndoableChange change) {
        BoardUndoCursor cursor = loadOrCreateCursor(boardId, userId);

        // A new action always abandons whatever redo branch existed above the current
        // top — the same rule as any standard undo/redo stack.
        stackEntryRepository.deleteAboveStackPosition(boardId, userId, cursor.getTopPosition());

        long newPosition = cursor.getTopPosition() + 1;
        // The session this event's operation was just applied to must still be resident —
        // we're running inside the same request that applied it, under the same lock.
        var session = sessionManager.getSession(boardId)
                .orElseThrow(() -> new IllegalStateException("No active session found for board: " + boardId));
        Map<UUID, Long> expectedVersions = UndoOperationFactory.currentVersions(session, change.affectedObjectIds());

        stackEntryRepository.save(BoardUndoStackEntry.create(
                boardId, userId, newPosition, event.getId(), event.getSequence(), change, expectedVersions));

        cursor.setTopPosition(newPosition);
        cursor.setMaxPosition(newPosition);
        cursor.touch();
        cursorRepository.save(cursor);
    }

    private BoardUndoCursor loadOrCreateCursor(UUID boardId, UUID userId) {
        return cursorRepository.findByIdForUpdate(boardId, userId)
                .orElseGet(() -> {
                    try {
                        return cursorRepository.save(BoardUndoCursor.empty(boardId, userId));
                    } catch (DataIntegrityViolationException concurrentlyCreated) {
                        return cursorRepository.findByIdForUpdate(boardId, userId)
                                .orElseThrow(() -> concurrentlyCreated);
                    }
                });
    }
}
