package com.ishan.syncCanvas.video.publisher;

import com.ishan.syncCanvas.video.dto.VideoSignalMessage;

import java.util.UUID;

/**
 * Wire envelope for the internal Redis video-signaling channel between SyncEngine
 * instances. Carries {@code boardId} and {@code targetUserId} (neither part of
 * {@link VideoSignalMessage} itself, which the recipient has no need to see either
 * echoed back) so a receiving instance knows the destination topic and which
 * locally-connected user, if any, to deliver to — and the originating instance's ID so
 * it can skip its own echo, mirroring
 * {@link com.ishan.syncCanvas.video.publisher.VideoEventEnvelope}.
 */
public record VideoSignalEnvelope(String originInstanceId, UUID boardId, UUID targetUserId, VideoSignalMessage message) {
}
