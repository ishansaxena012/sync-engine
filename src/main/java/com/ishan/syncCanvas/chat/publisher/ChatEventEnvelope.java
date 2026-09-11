package com.ishan.syncCanvas.chat.publisher;

import com.ishan.syncCanvas.chat.dto.ChatMessageResponse;

/**
 * Wire envelope for the internal Redis chat channel between SyncEngine instances.
 * Mirrors {@link com.ishan.syncCanvas.collaboration.presence.PresenceEventEnvelope} —
 * carries the originating instance's ID so that instance can skip its own echo.
 *
 * <p>The board id isn't a separate field here: it's already on the message, and
 * duplicating it would leave room for the two to disagree.
 */
public record ChatEventEnvelope(
        String originInstanceId,
        ChatMessageResponse message) {
}
