package com.ishan.syncCanvas.video.exception;

/** Thrown when someone other than the room's creator attempts to end it. */
public class VideoRoomAccessDeniedException extends RuntimeException {

    public VideoRoomAccessDeniedException(String message) {
        super(message);
    }
}
