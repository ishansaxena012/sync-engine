package com.ishan.syncCanvas.video.controller;

import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.video.dto.VideoErrorResponse;
import com.ishan.syncCanvas.video.dto.VideoSignalRequest;
import com.ishan.syncCanvas.video.dto.VideoStateRequest;
import com.ishan.syncCanvas.video.exception.VideoRoomAccessDeniedException;
import com.ishan.syncCanvas.video.exception.VideoRoomFullException;
import com.ishan.syncCanvas.video.exception.VideoRoomNotFoundException;
import com.ishan.syncCanvas.video.exception.VideoSignalRejectedException;
import com.ishan.syncCanvas.video.service.VideoRoomService;
import com.ishan.syncCanvas.video.service.VideoSessionTracker;
import com.ishan.syncCanvas.video.service.VideoSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * Video-room lifecycle, membership and mic/camera state over the existing STOMP
 * connection. Signaling (SDP/ICE/media) is handled by {@link VideoSignalService}; this
 * class only extracts the authenticated sender and delegates, on its own destinations,
 * kept off both the canvas operation stream and chat.
 *
 * <p>Protocol:
 * <ul>
 *   <li>SEND (no body) to {@code /app/boards/{boardId}/video/start} or {@code
 *       .../video/join} — the two are the same operation server-side; whichever the
 *       client uses is purely a UI distinction (a "start a call" button versus an
 *       "auto-join the active call" affordance).</li>
 *   <li>SEND (no body) to {@code /app/boards/{boardId}/video/leave}</li>
 *   <li>SEND (no body) to {@code /app/boards/{boardId}/video/end} — only the room's
 *       creator may do this.</li>
 *   <li>SEND {@code {"isMicEnabled","isCameraEnabled"}} to {@code
 *       /app/boards/{boardId}/video/state} — the caller's own mic/camera toggle,
 *       broadcast to the room as {@code PARTICIPANT_STATE_CHANGED}.</li>
 *   <li>SEND {@code {"type","targetUserId","sdp"|"candidate","signalingSessionId"}} to
 *       {@code /app/boards/{boardId}/video/signal} — one WebRTC SDP offer/answer or ICE
 *       candidate, forwarded to exactly one other active participant. See
 *       {@link VideoSignalService} for the full contract.</li>
 *   <li>RECEIVE room/membership/state changes on {@code /topic/boards/{boardId}/video}</li>
 *   <li>RECEIVE the caller's own full current roster, after start/join, on {@code
 *       /user/queue/boards/{boardId}/video/roster}</li>
 *   <li>RECEIVE the caller's own fresh signaling session id, after start/join, on
 *       {@code /user/queue/boards/{boardId}/video/session}</li>
 *   <li>RECEIVE a signaling frame addressed to the caller on {@code
 *       /user/queue/boards/{boardId}/video/signal} — deliberately not the public
 *       {@code /topic/boards/{boardId}/video} topic, since every participant subscribes
 *       to that and SDP/ICE data must reach only its intended target.</li>
 *   <li>RECEIVE send failures on {@code /user/queue/boards/{boardId}/video/errors}</li>
 * </ul>
 *
 * <p>Every request carries no client-supplied identity or timestamp at all — there is
 * no request body to parse for start/join/leave/end, so there is nothing for a client
 * to spoof. {@code userId}/{@code userName} always come from the authenticated STOMP
 * session, {@code joinedAt} from the server clock.
 *
 * <p>Subscribing to {@code /topic/boards/{boardId}/video} is already gated by {@code
 * StompAuthChannelInterceptor}, which board-checks every {@code /topic/boards/{id}/**}
 * SUBSCRIBE. That is not relied on alone: every SEND here re-checks board access in
 * {@link VideoRoomService}, so access revoked after a subscription was established
 * still takes effect on the very next operation.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class VideoController {

    private final VideoRoomService videoRoomService;
    private final VideoSessionTracker sessionTracker;
    private final SimpMessagingTemplate messagingTemplate;
    private final VideoSignalService videoSignalService;

    @MessageMapping("/boards/{boardId}/video/start")
    public void start(
            @DestinationVariable UUID boardId,
            Principal principal,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        handleJoin(boardId, principal, sessionId);
    }

    @MessageMapping("/boards/{boardId}/video/join")
    public void join(
            @DestinationVariable UUID boardId,
            Principal principal,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        handleJoin(boardId, principal, sessionId);
    }

    @MessageMapping("/boards/{boardId}/video/leave")
    public void leave(
            @DestinationVariable UUID boardId,
            Principal principal,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        UserPrincipal user = requireUser(principal);
        // Unregister first: if the leave itself throws (e.g. access was revoked), the
        // local session bookkeeping still reflects that this tab is done with video,
        // so a later disconnect for the same session cannot double-process it.
        sessionTracker.unregister(sessionId);
        videoRoomService.leave(boardId, user);
    }

    @MessageMapping("/boards/{boardId}/video/end")
    public void end(@DestinationVariable UUID boardId, Principal principal) {
        UserPrincipal user = requireUser(principal);
        videoRoomService.end(boardId, user);
    }

    @MessageMapping("/boards/{boardId}/video/state")
    public void updateState(
            @DestinationVariable UUID boardId,
            VideoStateRequest request,
            Principal principal) {
        UserPrincipal user = requireUser(principal);
        videoRoomService.updateParticipantState(boardId, user, request.isMicEnabled(), request.isCameraEnabled());
    }

    @MessageMapping("/boards/{boardId}/video/signal")
    public void signal(
            @DestinationVariable UUID boardId,
            VideoSignalRequest request,
            Principal principal) {
        UserPrincipal user = requireUser(principal);
        videoSignalService.relay(boardId, user, request);
    }

    /**
     * A participant's whole WebSocket connection dropped without an explicit LEAVE —
     * remove only their video-room membership. Board presence is a separate system
     * with its own disconnect handling ({@code PresenceController}); nothing here
     * touches it, and a user with another tab still in this same call is left alone
     * (see {@code VideoRoomService}'s per-user connection counter).
     *
     * <p>Deliberately does not also listen for {@code SessionUnsubscribeEvent}, unlike
     * {@code PresenceController} — that event fires for an unsubscribe from
     * <em>any</em> destination on the session, not specifically the video topic, and
     * ending a live call because a client unsubscribed from something unrelated (the
     * cursor topic, say) would be a surprising way to lose a call.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        sessionTracker.unregister(event.getSessionId())
                .ifPresent(boardUser ->
                        videoRoomService.removeParticipantOnDisconnect(boardUser.boardId(), boardUser.userId()));
    }

    private void handleJoin(UUID boardId, Principal principal, String sessionId) {
        UserPrincipal user = requireUser(principal);
        sessionTracker.register(sessionId, boardId, user.getId());
        videoRoomService.join(boardId, user);
    }

    private UserPrincipal requireUser(Principal principal) {
        if (!(principal instanceof UserPrincipal user)) {
            // Unreachable in practice — CONNECT is rejected without a valid JWT — but
            // an unauthenticated frame must never reach room state.
            throw new IllegalStateException("Unauthenticated video request rejected");
        }
        return user;
    }

    /**
     * Failures go back to the sender alone, on their private queue — mirrors {@code
     * ChatController}. A rejected request is nobody else's business, and broadcasting it
     * would show every participant an error for a request they never made. {@code code}
     * is a small, stable vocabulary the client maps to a user-facing message.
     */
    @MessageExceptionHandler
    public void handleException(Exception ex, @DestinationVariable UUID boardId, Principal principal) {
        log.warn("Rejected video request for board {}: {}", boardId, ex.getMessage());

        if (principal == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/boards/" + boardId + "/video/errors",
                new VideoErrorResponse(errorCode(ex), ex.getMessage(), Instant.now()));
    }

    private String errorCode(Exception ex) {
        if (ex instanceof VideoRoomFullException) {
            return "ROOM_FULL";
        }
        if (ex instanceof VideoRoomNotFoundException) {
            return "ROOM_NOT_FOUND";
        }
        if (ex instanceof VideoRoomAccessDeniedException || ex instanceof BoardAccessDeniedException) {
            return "FORBIDDEN";
        }
        if (ex instanceof VideoSignalRejectedException) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            return message.contains("too many") ? "RATE_LIMITED" : "SIGNALING_FAILED";
        }
        return "VIDEO_ERROR";
    }
}
