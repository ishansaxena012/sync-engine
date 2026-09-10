package com.ishan.syncCanvas.collaboration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.entity.Visibility;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.canvas.repository.CanvasObjectRepository;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardEventServiceTest {

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private BoardEventRepository eventRepository;
    @Mock
    private CanvasObjectRepository canvasObjectRepository;
    @Mock
    private BoardSnapshotService snapshotService;
    @Mock
    private OperationSequenceService operationSequenceService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private BoardEventService service;

    private final UUID boardId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BoardEventService(boardRepository, eventRepository, canvasObjectRepository,
                snapshotService, operationSequenceService, objectMapper);
    }

    private Board boardAt(long sequence) {
        Board board = Board.builder().id(boardId).name("b").ownerId(ownerId).visibility(Visibility.PRIVATE).build();
        board.setSequence(sequence);
        return board;
    }

    private MoveObjectOperation move() {
        return new MoveObjectOperation(UUID.randomUUID(), boardId, userId, Instant.now(),
                UUID.randomUUID(), 3L, 10.5, 20.5);
    }

    @Test
    void commitEventLocksRowIncrementsSequenceAndWritesExactlyOneEvent() throws Exception {
        Board board = boardAt(10L);
        when(boardRepository.findByIdForUpdate(boardId)).thenReturn(Optional.of(board));
        MoveObjectOperation op = move();

        long sequence = service.commitEvent(boardId, op, userId);

        assertThat(sequence).isEqualTo(11L);
        assertThat(board.getSequence()).isEqualTo(11L);
        verify(boardRepository).save(board);

        ArgumentCaptor<BoardEvent> captor = ArgumentCaptor.forClass(BoardEvent.class);
        verify(eventRepository).save(captor.capture());
        BoardEvent event = captor.getValue();
        assertThat(event.getBoardId()).isEqualTo(boardId);
        assertThat(event.getSequence()).isEqualTo(11L);
        assertThat(event.getOperationId()).isEqualTo(op.operationId());
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getOperationType()).isEqualTo("MOVE_OBJECT");
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getId()).isNotNull();
        // Payload is the complete operation and round-trips to an equal Operation.
        Operation restored = objectMapper.readValue(event.getPayload(), Operation.class);
        assertThat(restored).isEqualTo(op);
        // Sequence came from the row, not from anything the client sent — the
        // operation carries no sequence field at all.
        assertThat(event.getPayload()).doesNotContain("\"sequence\"");
    }

    @Test
    void sequenceIsScopedPerBoard() {
        UUID otherBoardId = UUID.randomUUID();
        Board other = Board.builder().id(otherBoardId).name("o").ownerId(ownerId).visibility(Visibility.PRIVATE).build();
        other.setSequence(99L);
        when(boardRepository.findByIdForUpdate(boardId)).thenReturn(Optional.of(boardAt(10L)));
        when(boardRepository.findByIdForUpdate(otherBoardId)).thenReturn(Optional.of(other));

        long a = service.commitEvent(boardId, move(), userId);
        long b = service.commitEvent(otherBoardId,
                new MoveObjectOperation(UUID.randomUUID(), otherBoardId, userId, Instant.now(),
                        UUID.randomUUID(), null, 1, 1), userId);

        assertThat(a).isEqualTo(11L);
        assertThat(b).isEqualTo(100L);
    }

    @Test
    void brandNewBoardStartsAtOneWithoutBootstrap() {
        Board board = boardAt(0L);
        when(boardRepository.findByIdForUpdate(boardId)).thenReturn(Optional.of(board));
        when(operationSequenceService.currentSequence(boardId)).thenReturn(0L);
        when(canvasObjectRepository.findByBoardId(boardId)).thenReturn(List.of());

        long sequence = service.commitEvent(boardId, move(), userId);

        assertThat(sequence).isEqualTo(1L);
        verify(snapshotService, never()).saveSnapshot(any(), anyLong(), anyList());
    }

    @Test
    void legacyPhase7BoardIsSeededFromRedisAndBaselinedWithASnapshot() {
        Board board = boardAt(0L);
        CanvasObject existing = CanvasObject.builder().id(UUID.randomUUID()).boardId(boardId)
                .type(CanvasObjectType.RECTANGLE).x(1).y(2).zindex(0).version(4L).build();
        when(boardRepository.findByIdForUpdate(boardId)).thenReturn(Optional.of(board));
        when(operationSequenceService.currentSequence(boardId)).thenReturn(250L);
        when(canvasObjectRepository.findByBoardId(boardId)).thenReturn(List.of(existing));

        long sequence = service.commitEvent(boardId, move(), userId);

        assertThat(sequence).isEqualTo(251L);
        verify(snapshotService).saveSnapshot(boardId, 250L, List.of(existing));
    }

    @Test
    void legacyBoardWithObjectsButNoRedisHistoryIsBaselinedAtZero() {
        Board board = boardAt(0L);
        CanvasObject existing = CanvasObject.builder().id(UUID.randomUUID()).boardId(boardId)
                .type(CanvasObjectType.RECTANGLE).x(1).y(2).zindex(0).version(1L).build();
        when(boardRepository.findByIdForUpdate(boardId)).thenReturn(Optional.of(board));
        when(operationSequenceService.currentSequence(boardId)).thenReturn(0L);
        when(canvasObjectRepository.findByBoardId(boardId)).thenReturn(List.of(existing));

        long sequence = service.commitEvent(boardId, move(), userId);

        assertThat(sequence).isEqualTo(1L);
        verify(snapshotService).saveSnapshot(boardId, 0L, List.of(existing));
    }

    @Test
    void missingBoardIsRejectedBeforeAnySequenceIsTouched() {
        when(boardRepository.findByIdForUpdate(boardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commitEvent(boardId, move(), userId))
                .isInstanceOf(BoardAccessDeniedException.class);

        verify(eventRepository, never()).save(any());
        verify(boardRepository, never()).save(any());
    }

    @Test
    void latestSequencePrefersPostgresButNeverFallsBelowRedisDuringTransition() {
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(boardAt(5L)));
        when(operationSequenceService.currentSequence(boardId)).thenReturn(250L);

        assertThat(service.getLatestSequence(boardId)).isEqualTo(250L);

        when(boardRepository.findById(boardId)).thenReturn(Optional.of(boardAt(300L)));
        assertThat(service.getLatestSequence(boardId)).isEqualTo(300L);
    }
}
