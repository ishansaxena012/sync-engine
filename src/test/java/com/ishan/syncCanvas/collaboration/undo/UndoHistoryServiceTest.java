package com.ishan.syncCanvas.collaboration.undo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.domain.RectanglePayload;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.event.BoardEvent;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UndoHistoryServiceTest {

    @Mock
    private BoardUndoCursorRepository cursorRepository;
    @Mock
    private BoardUndoStackEntryRepository stackEntryRepository;
    @Mock
    private BoardSessionManager sessionManager;

    private UndoHistoryService service;
    private BoardSession session;
    private CanvasObject object;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UndoHistoryService(cursorRepository, stackEntryRepository, sessionManager);
        session = new BoardSession(boardId);
        object = CanvasObject.builder()
                .id(UUID.randomUUID()).boardId(boardId).type(CanvasObjectType.RECTANGLE)
                .x(1).y(1).rotation(0).zindex(0)
                .payload(new RectanglePayload(1, 1, "#fff", "#000", 1, 0))
                .createdBy(userId).version(2L).build();
        session.initialize(List.of(object));
        when(sessionManager.getSession(boardId)).thenReturn(Optional.of(session));
    }

    private BoardEvent event(long sequence) {
        return BoardEvent.of(boardId, sequence, UUID.randomUUID(), userId, "MOVE_OBJECT", "{}");
    }

    @Test
    void firstOperationCreatesTheCursorAndAnEntryAtPositionOne() {
        when(cursorRepository.findByIdForUpdate(boardId, userId)).thenReturn(Optional.empty());
        when(cursorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UndoableChange change = new UndoableChange.MoveChange(object.getId(), 0, 0, 1, 1);

        service.recordNormalOperation(boardId, userId, event(1), change);

        // A brand-new cursor has nothing above position 0, so this is a harmless no-op —
        // but it's still called unconditionally rather than specially skipped.
        verify(stackEntryRepository).deleteAboveStackPosition(boardId, userId, 0L);
        ArgumentCaptor<BoardUndoStackEntry> captor = ArgumentCaptor.forClass(BoardUndoStackEntry.class);
        verify(stackEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getStackPosition()).isEqualTo(1L);
        assertThat(captor.getValue().getExpectedVersions()).isEqualTo(Map.of(object.getId(), 2L));

        ArgumentCaptor<BoardUndoCursor> cursorCaptor = ArgumentCaptor.forClass(BoardUndoCursor.class);
        verify(cursorRepository, org.mockito.Mockito.times(2)).save(cursorCaptor.capture());
        BoardUndoCursor finalCursor = cursorCaptor.getAllValues().get(cursorCaptor.getAllValues().size() - 1);
        assertThat(finalCursor.getTopPosition()).isEqualTo(1L);
        assertThat(finalCursor.getMaxPosition()).isEqualTo(1L);
    }

    @Test
    void aNewOperationAfterAnUndoAbandonsTheRedoBranch() {
        BoardUndoCursor cursor = BoardUndoCursor.empty(boardId, userId);
        cursor.setTopPosition(2);
        cursor.setMaxPosition(5); // positions 3,4,5 are a redo branch from before this new action
        when(cursorRepository.findByIdForUpdate(boardId, userId)).thenReturn(Optional.of(cursor));
        UndoableChange change = new UndoableChange.MoveChange(object.getId(), 0, 0, 1, 1);

        service.recordNormalOperation(boardId, userId, event(9), change);

        verify(stackEntryRepository).deleteAboveStackPosition(boardId, userId, 2L);
        ArgumentCaptor<BoardUndoStackEntry> captor = ArgumentCaptor.forClass(BoardUndoStackEntry.class);
        verify(stackEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getStackPosition()).isEqualTo(3L);
        assertThat(cursor.getTopPosition()).isEqualTo(3L);
        assertThat(cursor.getMaxPosition()).isEqualTo(3L); // positions 4 and 5 are gone for good
    }
}
