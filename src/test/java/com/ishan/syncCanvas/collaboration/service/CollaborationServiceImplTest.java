package com.ishan.syncCanvas.collaboration.service;

import com.ishan.syncCanvas.collaboration.event.BoardEvent;
import com.ishan.syncCanvas.collaboration.event.BoardEventService;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.publisher.OperationPublisher;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationBroadcaster;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionService;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceImplTest {

    @Mock
    private BoardSessionService boardSessionService;
    @Mock
    private OperationProcessor operationProcessor;
    @Mock
    private OperationPublisher operationPublisher;
    @Mock
    private OperationIdempotencyFilter operationIdempotencyFilter;
    @Mock
    private RedisOperationBroadcaster redisOperationBroadcaster;
    @Mock
    private BoardAccessGuard boardAccessGuard;
    @Mock
    private OperationSequenceService operationSequenceService;
    @Mock
    private BoardEventService boardEventService;
    @Mock
    private DirtySessionTracker dirtySessionTracker;

    @InjectMocks
    private CollaborationServiceImpl service;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(boardSessionService.openSession(boardId)).thenReturn(new BoardSession(boardId));
    }

    private MoveObjectOperation operation() {
        return new MoveObjectOperation(UUID.randomUUID(), boardId, userId, Instant.now(),
                UUID.randomUUID(), 1L, 10.0, 20.0);
    }

    @Test
    void acceptedOperationIsAppliedThenCommittedThenReplayedThenPublishedThenBroadcast() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        BoardEvent event = BoardEvent.of(boardId, 42L, op.operationId(), userId, op.type().name(), "{}");
        when(boardEventService.commitEvent(boardId, op, userId, null)).thenReturn(event);

        service.processOperation(boardId, op, userId);

        InOrder inOrder = inOrder(operationProcessor, boardEventService, operationSequenceService,
                operationPublisher, redisOperationBroadcaster);
        inOrder.verify(operationProcessor).process(op);
        inOrder.verify(boardEventService).commitEvent(boardId, op, userId, null);
        inOrder.verify(operationSequenceService).recordForReplay(
                eq(boardId), argThat((SequencedOperation s) -> s.sequence() == 42L && s.operation() == op));
        inOrder.verify(operationSequenceService).setCurrentSequence(boardId, 42L);
        inOrder.verify(operationPublisher).publish(
                eq(boardId), argThat((SequencedOperation s) -> s.sequence() == 42L && s.operation() == op));
        inOrder.verify(redisOperationBroadcaster).broadcast(42L, op);
        verify(boardSessionService, never()).closeSession(any());
    }

    @Test
    void databaseFailureEvictsSessionPublishesNothingAndReportsError() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        when(boardEventService.commitEvent(boardId, op, userId, null)).thenThrow(new RuntimeException("db down"));

        service.processOperation(boardId, op, userId);

        verify(boardSessionService).closeSession(boardId);
        verify(dirtySessionTracker).clearDirty(boardId);
        verify(operationSequenceService, never()).recordForReplay(any(), any());
        verify(operationSequenceService, never()).setCurrentSequence(any(), anyLong());
        verify(operationPublisher, never()).publish(any(), any());
        verify(redisOperationBroadcaster, never()).broadcast(anyLong(), any());
        verify(operationIdempotencyFilter).unregister(op.operationId());
        verify(operationPublisher).publishError(eq(boardId), any());
    }

    @Test
    void alreadyCommittedDuplicateIsDroppedQuietlyAndKeepsIdempotencyRegistration() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        when(boardEventService.commitEvent(boardId, op, userId, null))
                .thenThrow(new DataIntegrityViolationException("uk_board_event_board_operation"));

        service.processOperation(boardId, op, userId);

        verify(boardSessionService).closeSession(boardId);
        verify(operationPublisher, never()).publish(any(), any());
        verify(operationPublisher, never()).publishError(any(), any());
        verify(redisOperationBroadcaster, never()).broadcast(anyLong(), any());
        verify(operationIdempotencyFilter, never()).unregister(any());
    }

    @Test
    void redisFailureAfterCommitDoesNotRollBackOrBlockDelivery() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        BoardEvent event = BoardEvent.of(boardId, 7L, op.operationId(), userId, op.type().name(), "{}");
        when(boardEventService.commitEvent(boardId, op, userId, null)).thenReturn(event);
        doThrow(new RuntimeException("redis down")).when(operationSequenceService).recordForReplay(any(), any());

        service.processOperation(boardId, op, userId);

        verify(operationPublisher).publish(eq(boardId), argThat((SequencedOperation s) -> s.sequence() == 7L));
        verify(redisOperationBroadcaster).broadcast(7L, op);
        verify(operationIdempotencyFilter, never()).unregister(any());
        verify(operationPublisher, never()).publishError(any(), any());
        verify(boardSessionService, never()).closeSession(any());
    }

    @Test
    void rejectedOperationNeverReachesTheDatabase() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        doThrow(new VersionMismatchException(1L, 2L)).when(operationProcessor).process(op);

        service.processOperation(boardId, op, userId);

        verifyNoInteractions(boardEventService, operationSequenceService, redisOperationBroadcaster);
        verify(operationPublisher, never()).publish(any(), any());
        verify(boardSessionService, never()).closeSession(any());
        verify(operationIdempotencyFilter).unregister(op.operationId());
        verify(operationPublisher).publishError(eq(boardId), any());
    }

    @Test
    void duplicateOperationIdNeverReachesProcessingOrTheDatabase() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(false);

        service.processOperation(boardId, op, userId);

        verifyNoInteractions(boardEventService, operationProcessor, operationPublisher,
                redisOperationBroadcaster, operationSequenceService);
    }

    @Test
    void unauthorizedUserNeverReachesProcessingOrTheDatabase() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        doThrow(new BoardAccessDeniedException("denied")).when(boardAccessGuard).assertAccessible(boardId, userId);

        service.processOperation(boardId, op, userId);

        verifyNoInteractions(boardEventService, operationProcessor, operationSequenceService);
        verify(operationIdempotencyFilter).unregister(op.operationId());
    }
}
