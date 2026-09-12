package com.ishan.syncCanvas.video.publisher;

import com.ishan.syncCanvas.video.dto.VideoRoomEvent;

/**
 * Wire envelope for the internal Redis video-room channel between SyncEngine
 * instances. Mirrors {@link com.ishan.syncCanvas.collaboration.presence.PresenceEventEnvelope}
 * — carries the originating instance's ID so that instance can skip its own echo.
 */
public record VideoEventEnvelope(String originInstanceId, VideoRoomEvent event) {
}
