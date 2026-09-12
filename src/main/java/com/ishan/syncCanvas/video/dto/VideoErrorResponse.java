package com.ishan.syncCanvas.video.dto;

import java.time.Instant;

/**
 * Video-specific rejection reply on {@code /user/queue/boards/{boardId}/video/errors}.
 * {@code code} is a small, stable vocabulary (ROOM_FULL, ROOM_NOT_FOUND, FORBIDDEN,
 * RATE_LIMITED, SIGNALING_FAILED, VIDEO_ERROR) the client maps to a user-facing message
 * — kept separate from the generic {@code OperationErrorResponse} used elsewhere (chat,
 * canvas operations) since video's rejection vocabulary is its own.
 */
public record VideoErrorResponse(String code, String message, Instant timestamp) {
}
