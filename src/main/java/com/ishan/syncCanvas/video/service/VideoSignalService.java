package com.ishan.syncCanvas.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.video.dto.VideoSignalMessage;
import com.ishan.syncCanvas.video.dto.VideoSignalRequest;
import com.ishan.syncCanvas.video.dto.VideoSignalType;
import com.ishan.syncCanvas.video.exception.VideoSignalRejectedException;
import com.ishan.syncCanvas.video.publisher.VideoSignalBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Validates and forwards one WebRTC signaling frame (SDP offer/answer or ICE candidate)
 * from an active call participant to another. Purely a relay: the payload is opaque to
 * this class end to end — never parsed, rewritten, or inspected beyond its byte size and
 * whether it is a JSON object at all.
 *
 * <p>Every send re-derives sender identity from the authenticated {@link UserPrincipal},
 * never from the request body (which has no sender field to begin with), and re-checks
 * board access and live room membership on every single frame rather than trusting a
 * connection-time check — a call can end or a participant can leave mid-negotiation, and
 * the very next signal from or to them must be rejected, not delivered. Signaling-session
 * freshness is also checked whenever the client supplies one (see {@link VideoSignalRequest}).
 *
 * <p>Validation order: board access → active participant → session freshness (if
 * supplied) → valid target → message type/payload shape → payload size → rate limit →
 * delivery. Any failure aborts before local delivery or Redis relay, and is logged as
 * {@code VIDEO_SIGNAL_REJECTED} with no SDP/ICE content — only board/user/type metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoSignalService {

    @Value("${video.signaling.max-sdp-payload-bytes:65536}")
    private int maxSdpPayloadBytes;

    @Value("${video.signaling.max-ice-payload-bytes:8192}")
    private int maxIcePayloadBytes;

    private final BoardAccessGuard boardAccessGuard;
    private final VideoRoomService videoRoomService;
    private final VideoSignalRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final VideoSignalBroadcaster videoSignalBroadcaster;

    public void relay(UUID boardId, UserPrincipal sender, VideoSignalRequest request) {
        try {
            doRelay(boardId, sender, request);
            log.info("VIDEO_SIGNAL_SENT boardId={} userId={} targetUserId={} signalType={}",
                    boardId, sender.getId(), request == null ? null : request.targetUserId(),
                    request == null ? null : request.type());
        } catch (VideoSignalRejectedException ex) {
            log.warn("VIDEO_SIGNAL_REJECTED boardId={} userId={} signalType={} reason={}",
                    boardId, sender.getId(), request == null ? null : request.type(), ex.getMessage());
            throw ex;
        }
    }

    private void doRelay(UUID boardId, UserPrincipal sender, VideoSignalRequest request) {
        boardAccessGuard.assertAccessible(boardId, sender.getId());
        validateRequest(request);

        UUID targetUserId = request.targetUserId();
        if (targetUserId.equals(sender.getId())) {
            throw new VideoSignalRejectedException("Cannot send a signaling message to yourself");
        }

        if (!videoRoomService.isActiveParticipant(boardId, sender.getId())) {
            throw new VideoSignalRejectedException("You are not an active participant in this video call");
        }
        // Session freshness is only enforced when the client actually participates in
        // that protocol -- see VideoSignalRequest's Javadoc for why this is optional.
        if (request.signalingSessionId() != null
                && !videoRoomService.isCurrentSignalingSession(boardId, sender.getId(), request.signalingSessionId())) {
            throw new VideoSignalRejectedException("Stale signaling session — reconnect and rejoin the call");
        }
        // Scoped to this exact board's room, so this single check simultaneously rules
        // out a nonexistent target, a target who already left or whose call already
        // ended, and a target who is only active in a *different* board's call.
        if (!videoRoomService.isActiveParticipant(boardId, targetUserId)) {
            throw new VideoSignalRejectedException("Target user is not an active participant in this video call");
        }

        rateLimiter.assertWithinLimit(boardId, sender.getId(), request.type());
        validatePayloadSize(request.type(), request.payload());

        boolean isIce = request.type() == VideoSignalType.ICE_CANDIDATE;
        VideoSignalMessage message = new VideoSignalMessage(
                request.type(), sender.getId(),
                isIce ? null : request.payload(), isIce ? request.payload() : null,
                Instant.now().toEpochMilli());

        deliverLocally(boardId, targetUserId, message);
        videoSignalBroadcaster.broadcast(boardId, targetUserId, message);
    }

    private void validateRequest(VideoSignalRequest request) {
        if (request == null || request.type() == null) {
            throw new VideoSignalRejectedException("Signaling type is required");
        }
        if (request.targetUserId() == null) {
            throw new VideoSignalRejectedException("A target participant is required");
        }
        if (request.payload() == null) {
            throw new VideoSignalRejectedException("Signaling payload is required");
        }
        // A JSON object deserializes to a Map under Object-typed binding (see
        // VideoSignalRequest's Javadoc) -- anything else (a bare string, number, array)
        // is not a valid SDP/ICE payload shape.
        if (!(request.payload() instanceof java.util.Map)) {
            throw new VideoSignalRejectedException("Signaling payload must be a JSON object");
        }
    }

    private void deliverLocally(UUID boardId, UUID targetUserId, VideoSignalMessage message) {
        messagingTemplate.convertAndSendToUser(
                targetUserId.toString(),
                "/queue/boards/" + boardId + "/video/signal",
                message);
    }

    private void validatePayloadSize(VideoSignalType type, Object payload) {
        int maxBytes = type == VideoSignalType.ICE_CANDIDATE ? maxIcePayloadBytes : maxSdpPayloadBytes;
        int size;
        try {
            size = objectMapper.writeValueAsBytes(payload).length;
        } catch (Exception ex) {
            throw new VideoSignalRejectedException("Malformed signaling payload");
        }
        if (size > maxBytes) {
            throw new VideoSignalRejectedException("Signaling payload exceeds the maximum allowed size");
        }
    }
}
