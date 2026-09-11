package com.ishan.syncCanvas.collaboration.service;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.event.BoardEventService;
import com.ishan.syncCanvas.collaboration.event.DurableCommitException;
import com.ishan.syncCanvas.collaboration.exception.BoardMismatchException;
import com.ishan.syncCanvas.collaboration.exception.CollaborationException;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.publisher.OperationPublisher;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationBroadcaster;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionService;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import com.ishan.syncCanvas.collaboration.undo.UndoableChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates one client operation end to end:
 * <pre>
 *   validate → idempotency → authorize → [write lock]
 *     apply in memory → COMMIT (sequence + event, PostgreSQL) → replay/mirror/publish/broadcast
 *   [unlock]
 * </pre>
 * Nothing is published before the PostgreSQL transaction has committed, and nothing
 * after that commit can undo it — Redis and WebSocket delivery are best-effort
 * distribution on top of a durable fact.
 */
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
        private final BoardEventService boardEventService;
        private final DirtySessionTracker dirtySessionTracker;

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
                        BoardSession session = boardSessionService.openSession(boardId);

                        // The write lock is held across apply + commit + distribute so that,
                        // within this instance, in-memory application order, durable sequence
                        // order, and publish order are all the same order.
                        session.getLock().writeLock().lock();
                        try {
                                UndoableChange change = operationProcessor.process(operation);

                                OptionalLong sequence = commitDurably(boardId, operation, authenticatedUserId, change);
                                if (sequence.isEmpty()) {
                                        return; // already durable from an earlier delivery; nothing more to do
                                }

                                distribute(boardId, new SequencedOperation(sequence.getAsLong(), operation));
                        } finally {
                                session.getLock().writeLock().unlock();
                        }

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

                } catch (RuntimeException ex) {
                        operationIdempotencyFilter.unregister(operation.operationId());
                        throw ex;
                }

        }

        /**
         * Runs the PostgreSQL transaction that makes the operation durable. On any failure
         * the in-memory session is evicted: the operation was already applied to it, and
         * that uncommitted mutation must never reach current-state persistence — the
         * session reloads from committed state on next use.
         *
         * @return the committed sequence, or empty if the database reports this
         *         operationId is already durably committed (a redelivery that slipped past
         *         the Redis idempotency window), in which case it is silently dropped.
         */
        private OptionalLong commitDurably(UUID boardId, Operation operation, UUID userId, UndoableChange change) {
                try {
                        return OptionalLong.of(boardEventService.commitEvent(boardId, operation, userId, change).getSequence());
                } catch (DataIntegrityViolationException alreadyCommitted) {
                        // Only the (board_id, operation_id) constraint can fire here — the
                        // row-locked allocation makes a (board_id, sequence) collision impossible.
                        log.warn("Operation {} on board {} is already durably committed; dropping duplicate delivery",
                                        operation.operationId(), boardId);
                        evictSession(boardId);
                        return OptionalLong.empty();
                } catch (RuntimeException ex) {
                        evictSession(boardId);
                        throw new DurableCommitException(ex);
                }
        }

        /**
         * Discards the in-memory session after a durable-commit failure — the operation was
         * already applied to it, and that uncommitted mutation must never reach current-state
         * persistence. Marking the session {@code CLOSED} (in addition to removing it from the
         * registry) closes a narrow race: a persistence-scheduler tick that already read this
         * exact session reference out of the registry before this eviction runs is still
         * blocked on the session's write lock (held by the caller for the whole
         * apply+commit+distribute sequence) and only proceeds after this method returns and
         * the lock is released — by then the CLOSED state tells it to skip the flush instead
         * of persisting the never-committed mutation.
         */
        private void evictSession(UUID boardId) {
                boardSessionService.findSession(boardId).ifPresent(BoardSession::close);
                boardSessionService.closeSession(boardId);
                dirtySessionTracker.clearDirty(boardId);
        }

        /**
         * Everything after the commit is best-effort: a Redis or WebSocket failure here is
         * logged, never propagated, and never rolls back the durable operation — a client
         * that misses the live delivery recovers it through sync from the event store.
         */
        private void distribute(UUID boardId, SequencedOperation sequencedOperation) {
                long sequence = sequencedOperation.sequence();
                try {
                        operationSequenceService.recordForReplay(boardId, sequencedOperation);
                        operationSequenceService.setCurrentSequence(boardId, sequence);
                } catch (RuntimeException ex) {
                        log.error("Redis replay/mirror write failed for board {} sequence {}; operation remains durable in PostgreSQL",
                                        boardId, sequence, ex);
                }
                try {
                        operationPublisher.publish(boardId, sequencedOperation);
                } catch (RuntimeException ex) {
                        log.error("Local WebSocket publish failed for board {} sequence {}", boardId, sequence, ex);
                }
                redisOperationBroadcaster.broadcast(sequence, sequencedOperation.operation());
        }

}
