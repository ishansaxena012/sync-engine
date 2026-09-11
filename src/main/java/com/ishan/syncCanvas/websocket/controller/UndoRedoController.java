package com.ishan.syncCanvas.websocket.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.ishan.syncCanvas.collaboration.dto.UndoRedoResponse;
import com.ishan.syncCanvas.collaboration.undo.UndoRedoResult;
import com.ishan.syncCanvas.collaboration.undo.UndoRedoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server-authoritative undo/redo over the existing STOMP connection. The client sends no
 * body — it never determines what gets undone/redone, only asks the server to try.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class UndoRedoController {

    private final UndoRedoService undoRedoService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/boards/{boardId}/undo")
    public void undo(@DestinationVariable UUID boardId, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UndoRedoResult result = undoRedoService.undo(boardId, userId);
        reply(principal, boardId, "UNDO", result);
    }

    @MessageMapping("/boards/{boardId}/redo")
    public void redo(@DestinationVariable UUID boardId, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UndoRedoResult result = undoRedoService.redo(boardId, userId);
        reply(principal, boardId, "REDO", result);
    }

    private void reply(Principal principal, UUID boardId, String action, UndoRedoResult result) {
        String status = switch (result.status()) {
            case OK -> "OK";
            case NOTHING_TO_UNDO -> "NOTHING_TO_UNDO";
            case NOTHING_TO_REDO -> "NOTHING_TO_REDO";
            case CONFLICT -> action.equals("UNDO") ? "UNDO_CONFLICT" : "REDO_CONFLICT";
        };

        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/boards/" + boardId + "/" + action.toLowerCase(),
                new UndoRedoResponse(status, boardId, action, result.sourceSequence(), result.sequence()));
    }

    @MessageExceptionHandler
    public void handleException(Exception ex) {
        log.warn("Undo/redo request failed: {}", ex.getMessage());
    }
}
