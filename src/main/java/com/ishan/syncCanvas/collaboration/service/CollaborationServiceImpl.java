package com.ishan.syncCanvas.collaboration.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.exception.BoardMismatchException;
import com.ishan.syncCanvas.collaboration.exception.CollaborationException;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.publisher.OperationPublisher;
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
        // private final CollaborationService collaborationService;

        private void validateBoard(
                        UUID boardId,
                        Operation operation) {

                if (!boardId.equals(operation.boardId())) {
                        throw new BoardMismatchException();
                }
        }

        @Override
        public void processOperation(UUID boardId, Operation operation) {

                validateBoard(boardId, operation);

                try {
                        // log.debug("Processing {} on board {}", operation.type(), boardId);
                        boardSessionService.openSession(boardId);
                        // log.info("Calling processor...");
                        operationProcessor.process(operation);
                        // log.info("Processor finished.");
                        operationPublisher.publish(boardId, operation);

                } catch (CollaborationException ex) {

                        log.warn(
                                        "Operation {} failed on board {} : {}",
                                        operation.type(),
                                        boardId,
                                        ex.getMessage());

                        operationPublisher.publishError(

                                        new OperationErrorResponse(

                                                        "ERROR",

                                                        ex.getMessage(),

                                                        Instant.now()

                                        )

                        );

                        // Temporary.
                        // Later we'll publish an ERROR event back to the sender.

                }

        }

}