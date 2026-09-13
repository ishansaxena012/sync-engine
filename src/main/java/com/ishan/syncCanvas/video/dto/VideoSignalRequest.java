package com.ishan.syncCanvas.video.dto;

import java.util.UUID;

/**
 * Inbound WebRTC signaling frame sent to {@code /app/boards/{boardId}/video/signal}.
 * Exactly one of {@code sdp} (OFFER/ANSWER) or {@code candidate} (ICE_CANDIDATE) is
 * populated — the server never parses, rewrites, or inspects either, only forwards
 * whichever is present byte-for-byte to the resolved target as {@link #payload()}.
 *
 * <p>Deliberately typed as {@code Object} rather than a Jackson tree type
 * ({@code JsonNode}): the STOMP message broker's JSON converter and this project's own
 * REST/Redis {@code ObjectMapper} bean are backed by two different major Jackson
 * versions ({@code tools.jackson} vs {@code com.fasterxml.jackson}) that do not
 * understand each other's tree-node classes. A plain JSON object deserializes to a
 * {@code Map<String, Object>} under either version, so {@code Object} is the one shape
 * both sides of this DTO's lifecycle (inbound STOMP parsing, outbound STOMP delivery,
 * and the Redis cross-instance relay) can all handle without a version mismatch.
 *
 * <p>{@code signalingSessionId} is optional: when present, it must be the value most
 * recently received on {@code /user/queue/boards/{boardId}/video/session} (a fencing
 * token the caller only ever echoes, never chooses), and a stale value is rejected. A
 * client that doesn't yet participate in that protocol simply omits it, and the
 * freshness check is skipped — active-participant validation still applies regardless.
 *
 * <p>There is deliberately no sender field here at all: identity is always taken from
 * the authenticated STOMP session (see {@code VideoController#requireUser}), so an
 * extra {@code "senderId"} property in the raw JSON has nowhere to bind to and is
 * silently ignored by Jackson rather than trusted.
 */
public record VideoSignalRequest(
        VideoSignalType type, UUID targetUserId, Object sdp, Object candidate, String signalingSessionId) {

    public Object payload() {
        return sdp != null ? sdp : candidate;
    }
}
