package com.ishan.syncCanvas.websocket.controller;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import java.time.Instant;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CollaborationController {
    private final CollaborationService collaborationService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/boards/{boardId}/operations")
    public void processOperation(
            @DestinationVariable UUID boardId,
            Operation operation) {

        log.info("Controller received {}", operation.type());
        log.info("Received operation {}", operation);
        collaborationService.processOperation(boardId, operation);
    }

    @MessageExceptionHandler
    public void handleException(Exception e, @DestinationVariable UUID boardId) {
        log.error("Controller error", e);
        messagingTemplate.convertAndSend(
            "/topic/boards/" + boardId + "/errors", 
            new OperationErrorResponse(null, "ERROR", e.getMessage(), Instant.now())
        );
    }
}