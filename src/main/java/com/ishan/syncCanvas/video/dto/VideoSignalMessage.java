package com.ishan.syncCanvas.video.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Outbound WebRTC signaling frame delivered privately to exactly one target participant
 * on {@code /user/queue/boards/{boardId}/video/signal}. Never broadcast to the room's
 * {@code /topic/boards/{boardId}/video} lifecycle topic — every other participant in
 * the call must never see another pair's SDP/ICE exchange.
 *
 * <p>{@code senderId}/{@code senderName}/{@code timestamp} are always server-derived
 * from the authenticated caller and clock at forward time, never taken from the
 * inbound {@link VideoSignalRequest}.
 */
public record VideoSignalMessage(
        VideoSignalType type,
        UUID boardId,
        UUID senderId,
        String senderName,
        JsonNode payload,
        long timestamp) {
}
