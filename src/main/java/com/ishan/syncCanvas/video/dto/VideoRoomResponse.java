package com.ishan.syncCanvas.video.dto;

import com.ishan.syncCanvas.video.model.VideoParticipant;

import java.util.List;
import java.util.UUID;

/**
 * Canonical room state — returned by the REST snapshot endpoint. {@code active} is
 * exactly {@code !participants.isEmpty()}, spelled out explicitly so a client never has
 * to infer "no call" from an empty list.
 */
public record VideoRoomResponse(UUID boardId, boolean active, List<VideoParticipant> participants) {

    public static VideoRoomResponse of(UUID boardId, List<VideoParticipant> participants) {
        return new VideoRoomResponse(boardId, !participants.isEmpty(), participants);
    }
}
