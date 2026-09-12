package com.ishan.syncCanvas.video.dto;

/**
 * Private reply on {@code /user/queue/boards/{boardId}/video/session}, sent once per
 * join call. {@code signalingSessionId} is a fresh, server-generated fencing token the
 * caller must echo back on every subsequent {@code /video/signal} send — it becomes
 * stale the moment a newer connection for the same user joins the same room.
 */
public record VideoSignalingSession(String signalingSessionId) {
}
