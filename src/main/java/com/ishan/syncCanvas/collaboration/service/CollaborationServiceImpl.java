package com.ishan.syncCanvas.collaboration.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.exception.BoardMismatchException;
import com.ishan.syncCanvas.collaboration.exception.CollaborationException;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.publisher.OperationPublisher;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationBroadcaster;
import com.ishan.syncCanvas.collaboration.session.BoardSessionService;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CollaborationServiceImpl
                implements CollaborationService {

        private final BoardSessionService boardSessionService;
        private final OperationProcessor operationProcessor;
        private final OperationPublisher operationPublisher;
        private final OperationIdempotencyFilter operationIdempotencyFilter;
        private final RedisOperationBroadcaster redisOperationBroadcaster;
        private final BoardAccessGuard boardAccessGuard;
        private final OperationSequenceService operationSequenceService;

        private void validateBoard(
                        UUID boardId,
                        Operation operation) {

                if (!boardId.equals(operation.boardId())) {
                        throw new BoardMismatchException();
                }
        }

        @Override
        public void processOperation(UUID boardId, Operation operation, UUID authenticatedUserId) {

                validateBoard(boardId, operation);

                if (!operationIdempotencyFilter.registerIfNew(operation.operationId())) {
                        log.debug("Duplicate operation {} on board {} ignored", operation.operationId(), boardId);
                        return;
                }

                try {
                        boardAccessGuard.assertAccessible(boardId, authenticatedUserId);
                        boardSessionService.openSession(boardId);
                        operationProcessor.process(operation);

                        // Sequence is assigned only after the operation has actually been
                        // applied, so a rejected operation (version mismatch, missing
                        // object, ...) never consumes a number. Every published sequence is
                        // therefore a real, applied operation with no holes — clients can
                        // treat any gap as genuinely missed events rather than a false
                        // alarm from someone else's failed edit.
                        long sequence = operationSequenceService.nextSequence(boardId);
                        SequencedOperation sequencedOperation = new SequencedOperation(sequence, operation);

                        operationSequenceService.recordForReplay(boardId, sequencedOperation);
                        operationPublisher.publish(boardId, sequencedOperation);
                        redisOperationBroadcaster.broadcast(sequence, operation);

                } catch (CollaborationException ex) {

                        // Processing failed — undo the idempotency registration so a legitimate
                        // client retry with the same operationId isn't silently dropped as a
                        // duplicate for the rest of the TTL window.
                        operationIdempotencyFilter.unregister(operation.operationId());

                        log.warn(
                                        "Operation {} failed on board {} : {}",
                                        operation.type(),
                                        boardId,
                                        ex.getMessage());

                        operationPublisher.publishError(
                                        boardId,
                                        new OperationErrorResponse(
                                                        operation.operationId(),
                                                        "ERROR",
                                                        ex.getMessage(),
                                                        Instant.now()
                                        )
                        );

                        // Temporary.
                        // Later we'll publish an ERROR event back to the sender.

                } catch (RuntimeException ex) {
                        operationIdempotencyFilter.unregister(operation.operationId());
                        throw ex;
                }

        }

}