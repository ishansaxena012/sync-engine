package com.ishan.syncCanvas.collaboration.service;

import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.publisher.OperationPublisher;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationBroadcaster;
import com.ishan.syncCanvas.collaboration.session.BoardSessionService;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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

    @InjectMocks
    private CollaborationServiceImpl service;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private MoveObjectOperation operation() {
        return new MoveObjectOperation(UUID.randomUUID(), boardId, userId, Instant.now(),
                UUID.randomUUID(), 1L, 10.0, 20.0);
    }

    @Test
    void acceptedOperationIsSequencedOnlyAfterApplyThenRecordedPublishedAndBroadcast() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        when(operationSequenceService.nextSequence(boardId)).thenReturn(42L);

        service.processOperation(boardId, op, userId);

        InOrder inOrder = inOrder(operationProcessor, operationSequenceService, operationPublisher, redisOperationBroadcaster);
        inOrder.verify(operationProcessor).process(op);
        inOrder.verify(operationSequenceService).nextSequence(boardId);
        inOrder.verify(operationSequenceService).recordForReplay(
                eq(boardId), argThat((SequencedOperation s) -> s.sequence() == 42L && s.operation() == op));
        inOrder.verify(operationPublisher).publish(
                eq(boardId), argThat((SequencedOperation s) -> s.sequence() == 42L && s.operation() == op));
        inOrder.verify(redisOperationBroadcaster).broadcast(42L, op);
    }

    @Test
    void rejectedOperationNeverConsumesASequence() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        doThrow(new VersionMismatchException(1L, 2L)).when(operationProcessor).process(op);

        service.processOperation(boardId, op, userId);

        verify(operationSequenceService, never()).nextSequence(any());
        verify(operationSequenceService, never()).recordForReplay(any(), any());
        verify(operationPublisher, never()).publish(any(), any());
        verify(redisOperationBroadcaster, never()).broadcast(anyLong(), any());
        verify(operationIdempotencyFilter).unregister(op.operationId());
        verify(operationPublisher).publishError(eq(boardId), any());
    }

    @Test
    void duplicateOperationIdNeverReachesSequencing() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(false);

        service.processOperation(boardId, op, userId);

        verifyNoInteractions(operationSequenceService, operationProcessor, operationPublisher, redisOperationBroadcaster);
    }

    @Test
    void unauthorizedUserNeverConsumesASequence() {
        MoveObjectOperation op = operation();
        when(operationIdempotencyFilter.registerIfNew(op.operationId())).thenReturn(true);
        doThrow(new BoardAccessDeniedException("denied")).when(boardAccessGuard).assertAccessible(boardId, userId);

        service.processOperation(boardId, op, userId);

        verifyNoInteractions(operationSequenceService);
        verify(operationProcessor, never()).process(any());
        verify(operationIdempotencyFilter).unregister(op.operationId());
    }
}
