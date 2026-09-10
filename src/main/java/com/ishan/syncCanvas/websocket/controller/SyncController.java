package com.ishan.syncCanvas.websocket.controller;

import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.collaboration.sync.BoardSyncService;
import com.ishan.syncCanvas.collaboration.sync.SyncRequest;
import com.ishan.syncCanvas.collaboration.sync.SyncResponse;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Reconnect / missed-event sync over the existing STOMP connection.
 *
 * <p>Client contract, which is what actually closes the replay-vs-live race:
 * <ol>
 *   <li>Subscribe to {@code /topic/boards/{boardId}} (live) <em>first</em>.</li>
 *   <li>Subscribe to {@code /user/queue/boards/{boardId}/sync} for the reply.</li>
 *   <li>Then send {@code {"lastSequenceReceived": N}} to {@code /app/boards/{boardId}/sync}.</li>
 * </ol>
 * Because the live subscription is already active before the sync request is even
 * sent, any operation accepted between the server computing the replay and the reply
 * arriving is also delivered live — it can't be silently skipped. The only possible
 * outcome is a benign overlap (an operation appearing both in the replay and live),
 * which sequence numbers make safe: the client ignores anything at or below its last
 * applied sequence.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SyncController {

    private final BoardSyncService boardSyncService;
    private final BoardAccessGuard boardAccessGuard;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/boards/{boardId}/sync")
    public void sync(
            @DestinationVariable UUID boardId,
            SyncRequest request,
            Principal principal) {

        if (!(principal instanceof UserPrincipal user)) {
            return;
        }

        // Same access rule as sending an operation — a user who can't see a board
        // can't replay its history either, from Redis or from the durable log.
        if (!boardAccessGuard.isAccessible(boardId, user.getId())) {
            return;
        }

        SyncResponse response = boardSyncService.sync(boardId, request);

        messagingTemplate.convertAndSendToUser(
                user.getName(),
                "/queue/boards/" + boardId + "/sync",
                response);
    }

    @MessageExceptionHandler
    public void handleException(Exception ex) {
        log.warn("Dropped invalid sync message: {}", ex.getMessage());
    }
}
