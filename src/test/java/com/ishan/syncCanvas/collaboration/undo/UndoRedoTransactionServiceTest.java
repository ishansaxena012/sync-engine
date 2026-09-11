package com.ishan.syncCanvas.collaboration.undo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.domain.RectanglePayload;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.event.BoardEvent;
import com.ishan.syncCanvas.collaboration.event.BoardEventService;
import com.ishan.syncCanvas.collaboration.event.EventKind;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.processor.CreateObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.MoveObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.processor.RotateObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.DeleteObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.ChangePayloadHandler;
import com.ishan.syncCanvas.collaboration.processor.BulkMoveObjectHandler;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs through the real operation handlers (only the repositories are mocked), so a
 * detected "conflict" or a successful toggle reflects an actual version comparison
 * against actual session state, not a stubbed one.
 */
@ExtendWith(MockitoExtension.class)
class UndoRedoTransactionServiceTest {

    @Mock
    private BoardUndoCursorRepository cursorRepository;
    @Mock
    private BoardUndoStackEntryRepository stackEntryRepository;
    @Mock
    private BoardEventService boardEventService;
    @Mock
    private DirtySessionTracker dirtySessionTracker;

    private UndoRedoTransactionService service;
    private BoardSession session;
    private CanvasObject object;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BoardSessionManager unusedSessionManager = mock(BoardSessionManager.class);
        DirtySessionTracker unusedHandlerTracker = mock(DirtySessionTracker.class);
        OperationProcessor operationProcessor = new OperationProcessor(List.of(
                new CreateObjectHandler(unusedSessionManager, unusedHandlerTracker),
                new MoveObjectHandler(unusedSessionManager, unusedHandlerTracker),
                new RotateObjectHandler(unusedSessionManager, unusedHandlerTracker),
                new DeleteObjectHandler(unusedSessionManager, unusedHandlerTracker),
                new ChangePayloadHandler(unusedSessionManager, unusedHandlerTracker),
                new BulkMoveObjectHandler(unusedSessionManager, unusedHandlerTracker)));
        operationProcessor.registerHandlers();

        service = new UndoRedoTransactionService(
                cursorRepository, stackEntryRepository, operationProcessor, boardEventService, dirtySessionTracker);

        session = new BoardSession(boardId);
        object = CanvasObject.builder()
                .id(UUID.randomUUID())
                .boardId(boardId)
                .type(CanvasObjectType.RECTANGLE)
                .x(5).y(5).rotation(0).zindex(0)
                .payload(new RectanglePayload(1, 1, "#fff", "#000", 1, 0))
                .createdBy(userId)
                .version(2L)
                .build();
        session.initialize(List.of(object));
    }

    private BoardUndoStackEntry moveEntry(
            long stackPosition, long lastEventSequence, long expectedVersion, UndoStackStatus status) {
        UndoableChange change = new UndoableChange.MoveChange(object.getId(), 0, 0, 5, 5);
        BoardUndoStackEntry entry = BoardUndoStackEntry.create(
                boardId, userId, stackPosition, UUID.randomUUID(), lastEventSequence,
                change, Map.of(object.getId(), expectedVersion));
        entry.setStatus(status);
        return entry;
    }

    @Test
    void undoAppliesTheInverseAndSettlesTheEntryAndDecrementsTheCursor() {
        BoardUndoCursor cursor = BoardUndoCursor.empty(boardId, userId);
        cursor.setTopPosition(1);
        cursor.setMaxPosition(1);
        when(cursorRepository.findByIdForUpdate(boardId, userId)).thenReturn(Optional.of(cursor));
        BoardUndoStackEntry entry = moveEntry(1, 1L, 2L, UndoStackStatus.ACTIVE);
        when(stackEntryRepository.findByBoardIdAndUserIdAndStackPosition(boardId, userId, 1L))
                .thenReturn(Optional.of(entry));
        BoardEvent undoEvent = BoardEvent.of(boardId, 10L, UUID.randomUUID(), userId, "MOVE_OBJECT", "{}",
                EventKind.UNDO_OPERATION, entry.getLastEventId());
        when(boardEventService.commitUndoRedoEvent(eq(boardId), any(), eq(userId), eq(EventKind.UNDO_OPERATION), any()))
                .thenReturn(undoEvent);

        UndoRedoTransactionService.Outcome outcome = service.applyUndo(session, boardId, userId);

        assertThat(outcome.result()).isEqualTo(UndoRedoResult.ok(1L, 10L));
        assertThat(object.getX()).isEqualTo(0);
        assertThat(object.getY()).isEqualTo(0);
        assertThat(object.getVersion()).isEqualTo(3L);
        assertThat(outcome.toDistribute().sequence()).isEqualTo(10L);
        assertThat(outcome.toDistribute().operation()).isInstanceOf(MoveObjectOperation.class);

        ArgumentCaptor<BoardUndoStackEntry> savedEntry = ArgumentCaptor.forClass(BoardUndoStackEntry.class);
        verify(stackEntryRepository).save(savedEntry.capture());
        assertThat(savedEntry.getValue().getStatus()).isEqualTo(UndoStackStatus.UNDONE);
        assertThat(savedEntry.getValue().getExpectedVersions()).isEqualTo(Map.of(object.getId(), 3L));

        ArgumentCaptor<BoardUndoCursor> savedCursor = ArgumentCaptor.forClass(BoardUndoCursor.class);
        verify(cursorRepository).save(savedCursor.capture());
        assertThat(savedCursor.getValue().getTopPosition()).isEqualTo(0L);
    }

    @Test
    void undoDetectsAConflictWhenSomeoneElseMovedTheObjectSinceAndChangesNothing() {
        BoardUndoCursor cursor = BoardUndoCursor.empty(boardId, userId);
        cursor.setTopPosition(1);
        cursor.setMaxPosition(1);
        when(cursorRepository.findByIdForUpdate(boardId, userId)).thenReturn(Optional.of(cursor));
        // Entry still expects version 2, but the object is actually at version 2 per
        // setUp() — bump it to simulate a second user's edit landing afterward.
        object.setVersion(3L);
        BoardUndoStackEntry entry = moveEntry(1, 1L, 2L, UndoStackStatus.ACTIVE);
        when(stackEntryRepository.findByBoardIdAndUserIdAndStackPosition(boardId, userId, 1L))
                .thenReturn(Optional.of(entry));

        UndoRedoTransactionService.Outcome outcome = service.applyUndo(session, boardId, userId);

        assertThat(outcome.result().status()).isEqualTo(UndoRedoStatus.CONFLICT);
        assertThat(outcome.result().sourceSequence()).isEqualTo(1L);
        assertThat(outcome.toDistribute()).isNull();
        assertThat(object.getX()).isEqualTo(5); // untouched
        verify(stackEntryRepository, never()).save(any());
        verify(cursorRepository, never()).save(any());
        verify(boardEventService, never()).commitUndoRedoEvent(any(), any(), any(), any(), any());
    }

    @Test
    void undoWithNothingOnTheStackReportsNothingToUndo() {
        BoardUndoCursor cursor = BoardUndoCursor.empty(boardId, userId);
        when(cursorRepository.findByIdForUpdate(boardId, userId)).thenReturn(Optional.of(cursor));

        UndoRedoTransactionService.Outcome outcome = service.applyUndo(session, boardId, userId);

        assertThat(outcome.result()).isEqualTo(UndoRedoResult.nothingToUndo());
        verify(stackEntryRepository, never()).findByBoardIdAndUserIdAndStackPosition(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void redoReappliesTheForwardChangeAndAdvancesTheCursor() {
        BoardUndoCursor cursor = BoardUndoCursor.empty(boardId, userId);
        cursor.setTopPosition(0);
        cursor.setMaxPosition(1);
        when(cursorRepository.findByIdForUpdate(boardId, userId)).thenReturn(Optional.of(cursor));
        // Object currently at its post-undo state: (0,0), version 3 — matches what the
        // undo test above settled it to.
        object.setX(0);
        object.setY(0);
        object.setVersion(3L);
        // Represents the entry as it was left by a prior undo: last touched by event
        // sequence 10 (the undo), now expecting version 3 for its next (redo) toggle.
        BoardUndoStackEntry entry = moveEntry(1, 10L, 3L, UndoStackStatus.UNDONE);
        when(stackEntryRepository.findByBoardIdAndUserIdAndStackPosition(boardId, userId, 1L))
                .thenReturn(Optional.of(entry));
        BoardEvent redoEvent = BoardEvent.of(boardId, 11L, UUID.randomUUID(), userId, "MOVE_OBJECT", "{}",
                EventKind.REDO_OPERATION, entry.getLastEventId());
        when(boardEventService.commitUndoRedoEvent(eq(boardId), any(), eq(userId), eq(EventKind.REDO_OPERATION), any()))
                .thenReturn(redoEvent);

        UndoRedoTransactionService.Outcome outcome = service.applyRedo(session, boardId, userId);

        assertThat(outcome.result()).isEqualTo(UndoRedoResult.ok(10L, 11L));
        assertThat(object.getX()).isEqualTo(5);
        assertThat(object.getY()).isEqualTo(5);
        assertThat(object.getVersion()).isEqualTo(4L);

        ArgumentCaptor<BoardUndoCursor> savedCursor = ArgumentCaptor.forClass(BoardUndoCursor.class);
        verify(cursorRepository).save(savedCursor.capture());
        assertThat(savedCursor.getValue().getTopPosition()).isEqualTo(1L);
    }

    @Test
    void redoAtTheTopOfTheStackReportsNothingToRedo() {
        BoardUndoCursor cursor = BoardUndoCursor.empty(boardId, userId);
        cursor.setTopPosition(1);
        cursor.setMaxPosition(1);
        when(cursorRepository.findByIdForUpdate(boardId, userId)).thenReturn(Optional.of(cursor));

        UndoRedoTransactionService.Outcome outcome = service.applyRedo(session, boardId, userId);

        assertThat(outcome.result()).isEqualTo(UndoRedoResult.nothingToRedo());
    }
}
