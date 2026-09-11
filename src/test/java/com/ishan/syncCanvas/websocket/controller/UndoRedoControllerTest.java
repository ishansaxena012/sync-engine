package com.ishan.syncCanvas.websocket.controller;

import java.util.UUID;

import com.ishan.syncCanvas.collaboration.dto.UndoRedoResponse;
import com.ishan.syncCanvas.collaboration.undo.UndoRedoResult;
import com.ishan.syncCanvas.collaboration.undo.UndoRedoService;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UndoRedoControllerTest {

    @Mock
    private UndoRedoService undoRedoService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private UndoRedoController controller;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal =
            UserPrincipal.create(User.builder().id(userId).name("Ishan").email("ishan@example.com").build());

    @BeforeEach
    void setUp() {
        controller = new UndoRedoController(undoRedoService, messagingTemplate);
    }

    @Test
    void successfulUndoIsReportedToTheRequestingUserOnly() {
        when(undoRedoService.undo(boardId, userId)).thenReturn(UndoRedoResult.ok(5L, 6L));

        controller.undo(boardId, principal);

        verify(messagingTemplate).convertAndSendToUser(
                userId.toString(), "/queue/boards/" + boardId + "/undo",
                new UndoRedoResponse("OK", boardId, "UNDO", 5L, 6L));
    }

    @Test
    void nothingToUndoIsReportedAsSuchNotAsAnError() {
        when(undoRedoService.undo(boardId, userId)).thenReturn(UndoRedoResult.nothingToUndo());

        controller.undo(boardId, principal);

        verify(messagingTemplate).convertAndSendToUser(
                userId.toString(), "/queue/boards/" + boardId + "/undo",
                new UndoRedoResponse("NOTHING_TO_UNDO", boardId, "UNDO", null, null));
    }

    @Test
    void undoConflictIsReportedWithoutDestroyingAnyonesWork() {
        when(undoRedoService.undo(boardId, userId)).thenReturn(UndoRedoResult.conflict(3L));

        controller.undo(boardId, principal);

        verify(messagingTemplate).convertAndSendToUser(
                userId.toString(), "/queue/boards/" + boardId + "/undo",
                new UndoRedoResponse("UNDO_CONFLICT", boardId, "UNDO", 3L, null));
    }

    @Test
    void redoConflictIsReportedWithTheRedoSpecificStatus() {
        when(undoRedoService.redo(boardId, userId)).thenReturn(UndoRedoResult.conflict(3L));

        controller.redo(boardId, principal);

        verify(messagingTemplate).convertAndSendToUser(
                userId.toString(), "/queue/boards/" + boardId + "/redo",
                new UndoRedoResponse("REDO_CONFLICT", boardId, "REDO", 3L, null));
    }

    @Test
    void identityComesFromPrincipalNotAnyClientSuppliedField() {
        when(undoRedoService.undo(eq(boardId), eq(userId))).thenReturn(UndoRedoResult.nothingToUndo());

        controller.undo(boardId, principal);

        verify(undoRedoService).undo(boardId, userId);
    }
}
