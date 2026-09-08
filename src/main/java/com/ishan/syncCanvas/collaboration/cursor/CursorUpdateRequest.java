package com.ishan.syncCanvas.collaboration.cursor;

/**
 * Inbound cursor payload. Deliberately carries only position — userId and boardId are
 * always derived server-side (authenticated principal + destination variable), never
 * trusted from the client.
 */
public record CursorUpdateRequest(double x, double y) {
}
