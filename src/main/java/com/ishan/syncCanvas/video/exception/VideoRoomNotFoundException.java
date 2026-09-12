package com.ishan.syncCanvas.video.exception;

import java.util.UUID;

/** No active call exists for a board — thrown by an explicit END with nothing to end. */
public class VideoRoomNotFoundException extends RuntimeException {

    public VideoRoomNotFoundException(UUID boardId) {
        super("No active video call for board " + boardId);
    }
}
