package com.ishan.syncCanvas.collaboration.presence;

/**
 * Inbound presence payload. Deliberately carries only an action type — userId,
 * displayName and boardId are always derived server-side (authenticated principal +
 * destination variable), never trusted from the client.
 */
public record PresenceRequest(PresenceRequestType type) {
}
