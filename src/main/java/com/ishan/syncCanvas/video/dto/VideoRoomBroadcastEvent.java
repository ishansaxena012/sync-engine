package com.ishan.syncCanvas.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ishan.syncCanvas.video.model.VideoParticipant;

import java.util.UUID;

/**
 * Client-facing shape of a room-lifecycle event on {@code /topic/boards/{boardId}/video}
 * — translated from the richer internal {@link VideoRoomEvent} (which still carries the
 * full embedded roster, used for the Redis cross-instance envelope and by
 * {@code VideoRoomService}'s own internal logic/tests). Flat {@code {userId, userName,
 * isMicEnabled, isCameraEnabled}} rather than a nested participant object, and no
 * embedded roster: the client tracks membership incrementally from these events plus the
 * one-time {@link VideoRosterResponse} delivered privately on join.
 *
 * <p>{@code ROOM_ENDED} carries no participant fields at all — nobody "did" anything to
 * cause it, or it's ambiguous which of possibly several concurrent leaves actually ended
 * it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoRoomBroadcastEvent(
        VideoRoomEventType type,
        UUID userId,
        String userName,
        Boolean isMicEnabled,
        Boolean isCameraEnabled) {

    public static VideoRoomBroadcastEvent from(VideoRoomEvent event) {
        VideoParticipant participant = event.participant();
        if (participant == null) {
            return new VideoRoomBroadcastEvent(event.type(), null, null, null, null);
        }
        return new VideoRoomBroadcastEvent(
                event.type(), participant.userId(), participant.userName(),
                participant.isMicEnabled(), participant.isCameraEnabled());
    }
}
