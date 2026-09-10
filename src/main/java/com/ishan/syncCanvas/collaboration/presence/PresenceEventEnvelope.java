package com.ishan.syncCanvas.collaboration.presence;

import java.util.UUID;

/**
 * Wire envelope for the internal Redis presence channel between SyncEngine instances.
 * Mirrors {@link com.ishan.syncCanvas.collaboration.cursor.CursorEventEnvelope} — carries
 * the originating instance's ID so that instance can skip its own echo.
 */
public record PresenceEventEnvelope(
        String originInstanceId,
        UUID boardId,
        PresenceEvent event) {
}
