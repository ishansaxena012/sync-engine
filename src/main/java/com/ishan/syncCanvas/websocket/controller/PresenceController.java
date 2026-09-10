package com.ishan.syncCanvas.websocket.controller;

import com.ishan.syncCanvas.collaboration.presence.PresenceRequest;
import com.ishan.syncCanvas.collaboration.presence.PresenceService;
import com.ishan.syncCanvas.collaboration.presence.PresenceSessionTracker;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.UUID;

/**
 * Board presence (who's here, joined, left, idle) — a separate STOMP destination and
 * event model from both canvas operations and cursor sync, though it shares their
 * authentication/authorization infrastructure.
 *
 * <p>Orchestrates {@link PresenceSessionTracker} (local session bookkeeping) and
 * {@link PresenceService} (Redis state + broadcast) without either of those two
 * depending on each other — this is the one place both meet, whether triggered by an
 * explicit JOIN/HEARTBEAT/LEAVE message or by the STOMP session itself disappearing.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final PresenceSessionTracker sessionTracker;

    @MessageMapping("/boards/{boardId}/presence")
    public void handlePresence(
            @DestinationVariable UUID boardId,
            PresenceRequest request,
            Principal principal,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {

        if (!(principal instanceof UserPrincipal user) || request == null || request.type() == null) {
            return;
        }

        switch (request.type()) {
            case JOIN -> handleJoin(boardId, user, sessionId);
            case HEARTBEAT -> presenceService.heartbeat(boardId, user);
            case LEAVE -> handleLeave(sessionId);
        }
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        handleLeave(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        handleLeave(event.getSessionId());
    }

    @MessageExceptionHandler
    public void handleException(Exception ex) {
        // Presence is ephemeral and best-effort — log and drop rather than notifying
        // the client, so a malformed presence frame never affects canvas operations,
        // cursor sync, or Postgres persistence.
        log.warn("Dropped invalid presence message: {}", ex.getMessage());
    }

    private void handleJoin(UUID boardId, UserPrincipal user, String sessionId) {
        if (!presenceService.checkAccessible(boardId, user.getId())) {
            return;
        }

        if (!sessionTracker.register(sessionId, boardId, user.getId())) {
            return; // this exact session already joined — duplicate JOIN, no-op
        }

        boolean isFirstConnectionClusterWide = presenceService.recordConnection(boardId, user.getId());
        presenceService.completeJoin(boardId, user, isFirstConnectionClusterWide);
    }

    private void handleLeave(String sessionId) {
        sessionTracker.unregister(sessionId)
                .ifPresent(boardUser -> presenceService.releaseConnection(boardUser.boardId(), boardUser.userId()));
    }
}
