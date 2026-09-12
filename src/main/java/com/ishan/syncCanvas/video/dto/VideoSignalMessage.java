package com.ishan.syncCanvas.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Outbound WebRTC signaling frame delivered privately to exactly one target participant
 * on {@code /user/queue/boards/{boardId}/video/signal}. Never broadcast to the room's
 * {@code /topic/boards/{boardId}/video} lifecycle topic — every other participant in
 * the call must never see another pair's SDP/ICE exchange.
 *
 * <p>{@code fromUserId} and {@code timestamp} are always server-derived from the
 * authenticated caller and clock at forward time, never taken from the inbound
 * {@link VideoSignalRequest}. Exactly one of {@code sdp}/{@code candidate} is present,
 * mirroring whichever the sender populated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoSignalMessage(
        VideoSignalType type,
        UUID fromUserId,
        JsonNode sdp,
        JsonNode candidate,
        long timestamp) {
}
