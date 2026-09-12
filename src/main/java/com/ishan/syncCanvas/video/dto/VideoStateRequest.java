package com.ishan.syncCanvas.video.dto;

/** Inbound mic/camera toggle sent to {@code /app/boards/{boardId}/video/state}. */
public record VideoStateRequest(boolean isMicEnabled, boolean isCameraEnabled) {
}
