package com.ishan.syncCanvas.board;

import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.entity.Visibility;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.board.service.impl.BoardServiceImpl;
import com.ishan.syncCanvas.canvas.repository.CanvasObjectRepository;
import com.ishan.syncCanvas.collaboration.event.BoardEventRepository;
import com.ishan.syncCanvas.collaboration.event.BoardSnapshotRepository;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import com.ishan.syncCanvas.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

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
    private BoardSessionManager boardSessionManager;
    @Mock
    private DirtySessionTracker dirtySessionTracker;
    @Mock
    private OperationSequenceService operationSequenceService;
    @Mock
    private BoardEventRepository boardEventRepository;
    @Mock
    private BoardSnapshotRepository boardSnapshotRepository;

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
        verify(operationSequenceService).clearBoardState(boardId);
        verify(boardSessionManager).remove(boardId);
        verify(dirtySessionTracker).clearDirty(boardId);
        verify(canvasObjectRepository).deleteByBoardId(boardId);
        verify(boardRepository).delete(org.mockito.ArgumentMatchers.any(Board.class));
    }

    @Test
    void nonOwnerCannotDeleteAndNothingIsCleared() {
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board()));

        assertThatThrownBy(() -> boardService.deleteBoard(UUID.randomUUID(), boardId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(operationSequenceService, canvasObjectRepository, boardSessionManager,
                boardEventRepository, boardSnapshotRepository);
    }
}
