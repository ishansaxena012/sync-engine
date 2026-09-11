package com.ishan.syncCanvas.websocket.controller;

import com.ishan.syncCanvas.chat.dto.ChatSendRequest;
import com.ishan.syncCanvas.chat.service.ChatService;
import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * Board room chat over the existing STOMP connection.
 *
 * <p>Protocol:
 * <ul>
 *   <li>SEND {@code {"message": "..."}} to {@code /app/boards/{boardId}/chat}</li>
 *   <li>RECEIVE the canonical message on {@code /topic/boards/{boardId}/chat}</li>
 *   <li>RECEIVE send failures on {@code /user/queue/boards/{boardId}/chat/errors}</li>
 * </ul>
 *
 * <p>Kept strictly off {@code /topic/boards/{boardId}}: that destination carries the
 * sequenced canvas operation stream, and a chat message appearing on it would reach
 * clients that deserialize everything there as an {@code Operation}.
 *
 * <p>Subscribing to {@code /topic/boards/{boardId}/chat} is already gated by
 * {@link com.ishan.syncCanvas.websocket.security.StompAuthChannelInterceptor}, which
 * board-checks every {@code /topic/boards/{id}/**} SUBSCRIBE. That is not relied on
 * alone: the send path re-checks access in {@link ChatService#post} on every message,
 * so access revoked after a subscription was established still takes effect.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/boards/{boardId}/chat")
    public void sendMessage(
            @DestinationVariable UUID boardId,
            ChatSendRequest request,
            Principal principal) {

        if (!(principal instanceof UserPrincipal sender)) {
            // Unreachable in practice — CONNECT is rejected without a valid JWT — but
            // an unauthenticated frame must never reach persistence.
            throw new IllegalStateException("Unauthenticated chat message rejected");
        }

        // Board and sender come from the destination and the session; the body is only
        // ever consulted for the text itself.
        chatService.post(boardId, sender, request == null ? null : request.message());
    }

    /**
     * Failures go back to the sender alone, on their private queue — unlike a version
     * conflict, a rejected chat message is nobody else's business, and broadcasting it
     * would show every participant an error for a message they never saw.
     */
    @MessageExceptionHandler
    public void handleException(Exception ex, @DestinationVariable UUID boardId, Principal principal) {
        log.warn("Rejected chat message for board {}: {}", boardId, ex.getMessage());

        if (principal == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/boards/" + boardId + "/chat/errors",
                new OperationErrorResponse(null, "CHAT_ERROR", ex.getMessage(), Instant.now()));
    }
}
