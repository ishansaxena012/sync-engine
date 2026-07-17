package com.ishan.syncCanvas.websocket.controller;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import java.util.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class CollaborationController {
    private final CollaborationService collaborationService;

    @MessageMapping("/boards/{boardId}/operations")
    public void processOperation(
            @DestinationVariable UUID boardId,
            Operation operation) {
        collaborationService.processOperation(boardId, operation);
    }

    // ###########################################################
    // @MessageMapping("/boards/{boardId}/operations")
    // public void test(
    // @DestinationVariable UUID boardId,
    // String message) {

    // System.out.println("Received: " + message);

    // messagingTemplate.convertAndSend(
    // "/topic/boards/" + boardId,
    // "Echo: " + message);
    // }
    // #################################################
}