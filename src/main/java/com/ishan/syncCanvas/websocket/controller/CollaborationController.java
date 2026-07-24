package com.ishan.syncCanvas.websocket.controller;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CollaborationController {
    private final CollaborationService collaborationService;

    @MessageMapping("/boards/{boardId}/operations")
    public void processOperation(
            @DestinationVariable UUID boardId,
            Operation operation) {

        log.info("Controller received {}", operation.type());
        log.info("Received operation {}", operation);
        try {
            collaborationService.processOperation(boardId, operation);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid operation received: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing operation", e);
        }
    }
}