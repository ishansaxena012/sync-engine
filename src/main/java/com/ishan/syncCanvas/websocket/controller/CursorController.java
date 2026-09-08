package com.ishan.syncCanvas.websocket.controller;

import com.ishan.syncCanvas.collaboration.cursor.CursorService;
import com.ishan.syncCanvas.collaboration.cursor.CursorUpdateRequest;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Ephemeral cursor-position sync, kept fully separate from
 * {@link CollaborationController}'s persistent canvas-object operation pipeline —
 * cursor moves never enter that pipeline and never touch Postgres.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CursorController {

    private final CursorService cursorService;

    @MessageMapping("/boards/{boardId}/cursor")
    public void updateCursor(
            @DestinationVariable UUID boardId,
            CursorUpdateRequest request,
            Principal principal) {

        if (!(principal instanceof UserPrincipal user)) {
            return;
        }

        cursorService.updateCursor(boardId, user, request);
    }

    @MessageExceptionHandler
    public void handleException(Exception ex) {
        // Cursor updates are ephemeral and high-frequency — log and drop rather than
        // notifying the client, so a malformed cursor frame never affects canvas
        // operations or floods the board's error channel.
        log.warn("Dropped invalid cursor message: {}", ex.getMessage());
    }
}
