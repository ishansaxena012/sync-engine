package com.ishan.syncCanvas.collaboration.undo;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import com.ishan.syncCanvas.collaboration.publisher.OperationPublisher;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationBroadcaster;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionService;
import com.ishan.syncCanvas.collaboration.sync.OperationSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server-authoritative undo/redo: the client asks for "undo" or "redo", never submits an
 * inverse operation, prior state, or sequence number. Mirrors
 * {@code CollaborationServiceImpl}'s shape — board access, the session write lock held
 * across apply+commit+distribute, best-effort distribution after a durable commit — with
 * the transactional work in {@link UndoRedoTransactionService} so its
 * {@code @Transactional} methods go through the Spring proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UndoRedoService {

    private final BoardSessionService boardSessionService;
    private final BoardAccessGuard boardAccessGuard;
    private final UndoRedoTransactionService transactionService;
    private final OperationSequenceService operationSequenceService;
    private final OperationPublisher operationPublisher;
    private final RedisOperationBroadcaster redisOperationBroadcaster;

    public UndoRedoResult undo(UUID boardId, UUID userId) {
        boardAccessGuard.assertAccessible(boardId, userId);
        BoardSession session = boardSessionService.openSession(boardId);

        session.getLock().writeLock().lock();
        try {
            UndoRedoTransactionService.Outcome outcome = transactionService.applyUndo(session, boardId, userId);
            if (outcome.toDistribute() != null) {
                distribute(boardId, outcome.toDistribute());
            }
            return outcome.result();
        } finally {
            session.getLock().writeLock().unlock();
        }
    }

    public UndoRedoResult redo(UUID boardId, UUID userId) {
        boardAccessGuard.assertAccessible(boardId, userId);
        BoardSession session = boardSessionService.openSession(boardId);

        session.getLock().writeLock().lock();
        try {
            UndoRedoTransactionService.Outcome outcome = transactionService.applyRedo(session, boardId, userId);
            if (outcome.toDistribute() != null) {
                distribute(boardId, outcome.toDistribute());
            }
            return outcome.result();
        } finally {
            session.getLock().writeLock().unlock();
        }
    }

    /**
     * Same best-effort posture as {@code CollaborationServiceImpl.distribute}: the event
     * is already durably committed by this point, so a Redis or WebSocket failure here is
     * logged and never rolls anything back.
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
