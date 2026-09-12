package com.ishan.syncCanvas.video.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Inbound WebRTC signaling frame sent to {@code /app/boards/{boardId}/video/signal}.
 *
 * <p>{@code payload} is opaque SDP/ICE data — the server never parses, rewrites, or
 * inspects it, only forwards it byte-for-byte to the resolved target.
 *
 * <p>There is deliberately no sender field here at all: identity is always taken from
 * the authenticated STOMP session (see {@code VideoController#requireUser}), so an
 * extra {@code "senderId"} property in the raw JSON has nowhere to bind to and is
 * silently ignored by Jackson rather than trusted.
 */
public record VideoSignalRequest(VideoSignalType type, UUID targetUserId, JsonNode payload) {
}
