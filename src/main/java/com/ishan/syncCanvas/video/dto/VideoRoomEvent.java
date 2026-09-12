package com.ishan.syncCanvas.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ishan.syncCanvas.video.model.VideoParticipant;

import java.util.List;
import java.util.UUID;

/**
 * Outgoing event on {@code /topic/boards/{boardId}/video}.
 *
 * <p>Unlike {@code PresenceEvent} (a per-participant delta, with the full roster sent
 * separately and privately on join via a queue snapshot), every video event embeds the
 * complete current {@code participants} roster alongside whichever participant
 * triggered it. That is what lets a client that just subscribed and then sent
 * start/join learn the full room state from the ordinary broadcast reply itself — no
 * separate private "initial state" round trip is needed, as long as the client
 * subscribes to the topic before sending start/join (same subscribe-before-send
 * contract documented on {@code SyncController}).
 *
 * <p>{@code participant} is null for {@code ROOM_ENDED} (nobody "did" anything to
 * cause it, or it's ambiguous which of possibly several concurrent leaves actually
 * ended it) and for {@code ROOM_STATE}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoRoomEvent(
        VideoRoomEventType type,
        UUID boardId,
        VideoParticipant participant,
        List<VideoParticipant> participants,
        long timestamp) {
}
