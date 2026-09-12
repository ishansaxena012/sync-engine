package com.ishan.syncCanvas.video.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One participant of a board's video room — both the shape stored (as JSON) in Redis
 * and the shape sent to clients. There is no separate internal/wire split like
 * {@code PresenceRecord}/{@code PresenceEvent}: unlike presence, nothing here is
 * derived at read time (no idle/online status to compute), so one record safely serves
 * both roles without leaking any Redis-specific detail.
 *
 * <p>Entirely server-assigned. {@code userId}/{@code userName} come from the
 * authenticated {@code UserPrincipal} at join time, {@code joinedAt} from the server
 * clock — never from client input. {@code isMicEnabled}/{@code isCameraEnabled} default
 * to {@code true} at join and are only ever changed afterward by that same user's own
 * explicit state update (see {@code VideoRoomService#updateParticipantState}), never by
 * anyone else.
 */
public record VideoParticipant(
        UUID userId, String userName, Instant joinedAt, boolean isMicEnabled, boolean isCameraEnabled) {
}
