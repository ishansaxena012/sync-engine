package com.ishan.syncCanvas.video.exception;

/**
 * A signaling frame was rejected before forwarding — the sender or target is not an
 * active call participant, the target is invalid, the payload is missing/malformed/too
 * large, or the sender is signaling too fast. Carries only a client-safe message; never
 * wraps an internal exception whose detail shouldn't reach the sender.
 */
public class VideoSignalRejectedException extends RuntimeException {

    public VideoSignalRejectedException(String message) {
        super(message);
    }
}
