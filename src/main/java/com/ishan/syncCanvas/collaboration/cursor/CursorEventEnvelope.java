package com.ishan.syncCanvas.collaboration.cursor;

import java.util.UUID;

/**
 * Wire envelope for the internal Redis cursor channel between SyncEngine instances.
 * Mirrors {@link com.ishan.syncCanvas.collaboration.publisher.RedisOperationMessage} —
 * carries the originating instance's ID so that instance can skip its own echo.
 */
public record CursorEventEnvelope(
        String originInstanceId,
        UUID boardId,
        CursorEvent event) {
}
