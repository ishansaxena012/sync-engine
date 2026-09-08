package com.ishan.syncCanvas.collaboration.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.entity.Visibility;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.exception.BoardMismatchException;
import com.ishan.syncCanvas.collaboration.exception.CollaborationException;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.publisher.OperationPublisher;
import com.ishan.syncCanvas.collaboration.publisher.RedisOperationBroadcaster;
import com.ishan.syncCanvas.collaboration.session.BoardSessionService;
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
        private final BoardRepository boardRepository;

        private void validateBoard(
                        UUID boardId,
                        Operation operation) {

                if (!boardId.equals(operation.boardId())) {
                        throw new BoardMismatchException();
                }
        }

        /**
         * Confirms the authenticated caller may access this board before any operation
         * touching it is applied. Authenticating the STOMP connection alone is not
         * enough — without this, any logged-in user could send operations for any
         * board's ID, private or not.
         */
        private void assertBoardAccessible(UUID boardId, UUID userId) {
                Board board = boardRepository.findById(boardId)
                                .orElseThrow(() -> new BoardAccessDeniedException("Board not found: " + boardId));

                if (!board.getOwnerId().equals(userId) && board.getVisibility() != Visibility.PUBLIC) {
                        throw new BoardAccessDeniedException("You do not have access to board " + boardId);
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
                        assertBoardAccessible(boardId, authenticatedUserId);
                        // log.debug("Processing {} on board {}", operation.type(), boardId);
                        boardSessionService.openSession(boardId);
                        // log.info("Calling processor...");
                        operationProcessor.process(operation);
                        // log.info("Processor finished.");
                        operationPublisher.publish(boardId, operation);
                        redisOperationBroadcaster.broadcast(operation);

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