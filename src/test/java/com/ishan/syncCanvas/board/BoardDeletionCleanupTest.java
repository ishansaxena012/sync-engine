package com.ishan.syncCanvas.board;

import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.entity.Visibility;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.board.service.impl.BoardServiceImpl;
import com.ishan.syncCanvas.canvas.repository.CanvasObjectRepository;
import com.ishan.syncCanvas.chat.repository.ChatMessageRepository;
import com.ishan.syncCanvas.collaboration.cursor.CursorService;
import com.ishan.syncCanvas.collaboration.event.BoardEventRepository;
import com.ishan.syncCanvas.collaboration.event.BoardSnapshotRepository;
import com.ishan.syncCanvas.collaboration.lifecycle.BoardClosedEvent;
import com.ishan.syncCanvas.collaboration.lifecycle.BoardClosureBroadcaster;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.presence.PresenceService;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import com.ishan.syncCanvas.collaboration.undo.BoardUndoCursorRepository;
import com.ishan.syncCanvas.collaboration.undo.BoardUndoStackEntryRepository;
import com.ishan.syncCanvas.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardDeletionCleanupTest {

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private UserService userService;
    @Mock
    private CanvasObjectRepository canvasObjectRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private BoardSessionManager boardSessionManager;
    @Mock
    private DirtySessionTracker dirtySessionTracker;
    @Mock
    private OperationSequenceService operationSequenceService;
    @Mock
    private BoardEventRepository boardEventRepository;
    @Mock
    private BoardSnapshotRepository boardSnapshotRepository;
    @Mock
    private BoardUndoStackEntryRepository boardUndoStackEntryRepository;
    @Mock
    private BoardUndoCursorRepository boardUndoCursorRepository;
    @Mock
    private PresenceService presenceService;
    @Mock
    private CursorService cursorService;
    @Mock
    private BoardClosureBroadcaster boardClosureBroadcaster;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BoardServiceImpl boardService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID boardId = UUID.randomUUID();

    private Board board() {
        return Board.builder().id(boardId).name("b").ownerId(ownerId).visibility(Visibility.PRIVATE).build();
    }

    @Test
    void deleteClearsEventsSnapshotsSequenceAndReplayStateAlongsideExistingCleanup() {
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board()));

        boardService.deleteBoard(ownerId, boardId);

        verify(boardEventRepository).deleteByBoardId(boardId);
        verify(boardSnapshotRepository).deleteByBoardId(boardId);
        verify(boardUndoStackEntryRepository).deleteByBoardId(boardId);
        verify(boardUndoCursorRepository).deleteByBoardId(boardId);
        verify(operationSequenceService).clearBoardState(boardId);
        verify(presenceService).clearBoardState(boardId);
        verify(cursorService).clearBoardState(boardId);
        verify(boardSessionManager).remove(boardId);
        verify(dirtySessionTracker).clearDirty(boardId);
        verify(canvasObjectRepository).deleteByBoardId(boardId);
        verify(chatMessageRepository).deleteByBoardId(boardId);
        verify(boardRepository).delete(org.mockito.ArgumentMatchers.any(Board.class));

        // No Spring transaction is active in this plain unit test, so the notification
        // fires immediately rather than deferred to afterCommit — see BoardServiceImpl.
        ArgumentCaptor<BoardClosedEvent> captor = ArgumentCaptor.forClass(BoardClosedEvent.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/boards/" + boardId + "/closed"), captor.capture());
        assertThat(captor.getValue().boardId()).isEqualTo(boardId);
        verify(boardClosureBroadcaster).broadcast(captor.getValue());
    }

    @Test
    void nonOwnerCannotDeleteAndNothingIsCleared() {
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board()));

        assertThatThrownBy(() -> boardService.deleteBoard(UUID.randomUUID(), boardId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(operationSequenceService, canvasObjectRepository, boardSessionManager,
                boardEventRepository, boardSnapshotRepository, boardUndoStackEntryRepository,
                boardUndoCursorRepository, presenceService, cursorService, boardClosureBroadcaster,
                messagingTemplate, chatMessageRepository);
    }
}
