package com.ishan.syncCanvas.collaboration.service;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
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
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void processOperation(
            UUID boardId,
            Operation operation) {

        validateBoard(boardId, operation);

        log.debug(
                "Processing {} on board {}",
                operation.type(),
                boardId);

        boardSessionService.openSession(boardId);

        operationProcessor.process(operation);

        messagingTemplate.convertAndSend(
                "/topic/boards/" + boardId,
                operation);
    }

    private void validateBoard(
            UUID boardId,
            Operation operation) {

        if (!boardId.equals(operation.boardId())) {
            throw new IllegalArgumentException(
                    "Board ID mismatch between destination and operation payload.");
        }
    }
}