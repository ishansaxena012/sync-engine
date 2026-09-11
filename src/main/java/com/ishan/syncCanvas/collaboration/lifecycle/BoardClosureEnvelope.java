package com.ishan.syncCanvas.collaboration.lifecycle;

/**
 * Wire envelope for the internal Redis board-closure channel between SyncEngine
 * instances. Mirrors {@link com.ishan.syncCanvas.collaboration.cursor.CursorEventEnvelope} —
 * carries the originating instance's ID so that instance can skip its own echo.
 */
public record BoardClosureEnvelope(String originInstanceId, BoardClosedEvent event) {
}
