package com.ishan.syncCanvas.collaboration.cursor;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Outgoing presence event for one user's cursor. {@code x}/{@code y} are boxed so a
 * {@code CURSOR_LEAVE} event can omit them entirely (no position to report) rather than
 * sending a misleading 0/0.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorEvent(
        CursorEventType type,
        UUID userId,
        String displayName,
        String color,
        Double x,
        Double y,
        long timestamp) {
}
