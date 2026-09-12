package com.ishan.syncCanvas.video.exception;

import java.util.UUID;

/** A join/start was rejected because the room already holds {@code video.room.max-participants}. */
public class VideoRoomFullException extends RuntimeException {

    public VideoRoomFullException(UUID boardId, int maxParticipants) {
        super("Video call for board " + boardId + " is full (max " + maxParticipants + " participants)");
    }
}
