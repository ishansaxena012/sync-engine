package com.ishan.syncCanvas.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.video.dto.VideoRoomEvent;
import com.ishan.syncCanvas.video.dto.VideoRoomEventType;
import com.ishan.syncCanvas.video.dto.VideoRoomResponse;
import com.ishan.syncCanvas.video.exception.VideoRoomAccessDeniedException;
import com.ishan.syncCanvas.video.exception.VideoRoomNotFoundException;
import com.ishan.syncCanvas.video.model.VideoParticipant;
import com.ishan.syncCanvas.video.publisher.VideoEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ephemeral video-room lifecycle and membership. Like {@code PresenceService} and
 * {@code CursorService}, this has no JPA repository and never touches Postgres —
 * Redis is the sole source of truth, and every key this class writes is deleted when
 * the room ends (explicitly, by draining to zero participants, or by board deletion).
 * Nothing here handles WebRTC signaling, SDP, ICE, or media — that is a later phase.
 *
 * <p>Four Redis keys per board, all sharing the {@code video:board:{boardId}:} prefix
 * used by {@code PresenceService}'s own {@code presence:board:{boardId}:} scheme:
 * <ul>
 *   <li>{@code participants} — Hash, {@code userId -> JSON(VideoParticipant)}. The
 *       source of the room's roster.</li>
 *   <li>{@code connections} — Hash, {@code userId -> tab/session count}, mutated with
 *       {@code HINCRBY} (atomic). Mirrors {@code PresenceService}'s per-user connection
 *       counter: a user with two tabs open is one participant with a count of 2, and
 *       only the tab that brings their count to zero actually removes them — the same
 *       mechanism that keeps a board-presence heartbeat from vanishing when one of a
 *       user's tabs closes.</li>
 *   <li>{@code count} — String, the room's total distinct-participant count, mutated
 *       with {@code INCR}/{@code DECR} (atomic). This, not a post-hoc {@code HLEN} on
 *       the participants hash, is what decides "did this call just start" and "is the
 *       room now empty" — {@code INCR}/{@code DECR} return a value unique to the caller
 *       that performed it, so two users finishing their last tab at the same instant
 *       can't both observe "empty" and both attempt to tear the room down.</li>
 *   <li>{@code creator} — String, set once via {@code SETNX} (atomic, first writer
 *       wins) to whichever user's join/start call is the one that actually created the
 *       room. This is who {@link #end} checks against.</li>
 * </ul>
 *
 * <p>Every join/start reply — not just the public broadcast — is also sent privately to
 * the caller on {@code /user/queue/boards/{boardId}/video/state}, carrying the full
 * current roster. A repeat join from a second tab produces no public broadcast (nothing
 * changed for anyone else), so without this private reply that tab would have no way to
 * learn who is already in the call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoRoomService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final VideoEventBroadcaster videoEventBroadcaster;
    private final BoardAccessGuard boardAccessGuard;

    // ---------------------------------------------------------------- public API

    /**
     * Starts or joins a board's video call — the two are the same operation. If the
     * room already has a participant, this adds the caller to it rather than creating
     * a second one; if it is empty or missing, this call creates it and the caller
     * becomes its recorded creator.
     *
     * <p>Redis failures propagate rather than being swallowed: unlike chat (where
     * Postgres is the durable source of truth and Redis is only a relay), Redis
     * <em>is</em> the source of truth for video-room state, so a failed write here must
     * surface as a real error, not a silently-accepted no-op.
     */
    public VideoRoomEvent join(UUID boardId, UserPrincipal user) {
        boardAccessGuard.assertAccessible(boardId, user.getId());
        UUID userId = user.getId();

        // First writer wins; a no-op if the room already has a creator. Deliberately
        // unconditional — cheaper than checking existence first, and SETNX is already
        // exactly the right atomic primitive for "only the first caller gets this".
        redisTemplate.opsForValue().setIfAbsent(creatorKey(boardId), userId.toString());

        long tabCount = incrementConnections(boardId, userId);
        VideoRoomEvent state;
        if (tabCount == 1) {
            VideoParticipant participant = new VideoParticipant(userId, user.getDisplayName(), Instant.now());
            hashOps().put(participantsKey(boardId), userId.toString(), writeJson(participant));
            long roomCount = incrementRoomCount(boardId);
            VideoRoomEventType type = roomCount == 1 ? VideoRoomEventType.ROOM_STARTED : VideoRoomEventType.PARTICIPANT_JOINED;
            state = event(type, boardId, participant, currentRoster(boardId));
            publish(state);
        } else {
            // Another tab/session for this same user already holds this room open —
            // nothing changed for anyone else, so nothing is broadcast.
            state = event(VideoRoomEventType.ROOM_STATE, boardId, null, currentRoster(boardId));
        }
        sendPrivateState(boardId, user.getName(), state);
        return state;
    }

    /**
     * Removes the caller from a board's video call. A no-op (returns empty) if another
     * of the caller's own tabs/sessions is still in the room — see the {@code
     * connections} key doc above.
     */
    public Optional<VideoRoomEvent> leave(UUID boardId, UserPrincipal user) {
        boardAccessGuard.assertAccessible(boardId, user.getId());
        return removeParticipant(boardId, user.getId());
    }

    /**
     * Same removal as {@link #leave}, without the access check — used when a
     * WebSocket session disconnects and there is no live {@link UserPrincipal} to ask,
     * only the {@code (boardId, userId)} the disconnecting session was tracked under.
     * Cleanup must never be blocked by access: a user whose board access was revoked
     * mid-call still needs their stale membership removed.
     */
    public void removeParticipantOnDisconnect(UUID boardId, UUID userId) {
        removeParticipant(boardId, userId);
    }

    /**
     * Ends the entire call. Only the room's recorded creator may do this — everyone
     * else gets {@link VideoRoomAccessDeniedException}. A room with nobody left in it
     * has already torn itself down via {@link #leave}'s drain-to-zero path, so calling
     * this on a room nobody ever started, or one that already ended, throws
     * {@link VideoRoomNotFoundException} rather than silently succeeding.
     */
    public VideoRoomEvent end(UUID boardId, UserPrincipal user) {
        boardAccessGuard.assertAccessible(boardId, user.getId());

        String creatorId = redisTemplate.opsForValue().get(creatorKey(boardId));
        if (creatorId == null) {
            throw new VideoRoomNotFoundException(boardId);
        }
        if (!creatorId.equals(user.getId().toString())) {
            throw new VideoRoomAccessDeniedException(
                    "Only the participant who started this video call can end it.");
        }

        deleteRoomKeys(boardId);
        VideoRoomEvent event = event(VideoRoomEventType.ROOM_ENDED, boardId, null, List.of());
        publish(event);
        return event;
    }

    /** Current room state for the REST snapshot endpoint. Empty participants means no active call. */
    public VideoRoomResponse getRoomState(UUID boardId, UUID userId) {
        boardAccessGuard.assertAccessible(boardId, userId);
        return VideoRoomResponse.of(boardId, currentRoster(boardId));
    }

    /**
     * True if the given user currently holds an active connection in this board's video
     * room. No access check here — this is a pure membership query used by
     * {@code VideoSignalService} to validate signaling senders and targets, and the
     * caller has already checked board access itself. Scoped to a single board's
     * participants hash, so this doubles as the "same room" and "not stale" check:
     * a user who left, whose call ended, or who is only active in a different board's
     * call, is indistinguishable from one who was never here at all.
     */
    public boolean isActiveParticipant(UUID boardId, UUID userId) {
        return Boolean.TRUE.equals(hashOps().hasKey(participantsKey(boardId), userId.toString()));
    }

    /**
     * Wipes a board's video-room state with no broadcast — called from board deletion
     * alongside {@code PresenceService}/{@code CursorService}'s own {@code
     * clearBoardState}. Silent for the same reason theirs are: {@code BOARD_CLOSED} on
     * {@code /topic/boards/{boardId}/closed} already tells every connected client the
     * whole board is gone, so a redundant {@code ROOM_ENDED} here would tell them
     * nothing new. Failures are logged, not propagated — a Redis hiccup during cleanup
     * must not abort the board deletion itself.
     */
    public void clearBoardState(UUID boardId) {
        try {
            deleteRoomKeys(boardId);
        } catch (Exception ex) {
            log.error("Failed to clear video room state for board {}", boardId, ex);
        }
    }

    // ---------------------------------------------------------------- internals

    private Optional<VideoRoomEvent> removeParticipant(UUID boardId, UUID userId) {
        if (!roomExists(boardId)) {
            // Already ended (explicit END, drained to zero by someone else, or board
            // deletion) — a late disconnect/leave for this room is a no-op, not a
            // re-creation of stale keys.
            return Optional.empty();
        }

        long tabCount = decrementConnections(boardId, userId);
        if (tabCount > 0) {
            return Optional.empty(); // another tab/session for this user is still here
        }

        // Read the stored record before removing it — a disconnect-triggered removal
        // has no live UserPrincipal to ask for a display name.
        VideoParticipant leaving = readParticipant(boardId, userId);
        hashOps().delete(participantsKey(boardId), userId.toString());
        long roomCount = decrementRoomCount(boardId);

        VideoRoomEvent event;
        if (roomCount <= 0) {
            deleteRoomKeys(boardId);
            event = event(VideoRoomEventType.ROOM_ENDED, boardId, leaving, List.of());
        } else {
            event = event(VideoRoomEventType.PARTICIPANT_LEFT, boardId, leaving, currentRoster(boardId));
        }
        publish(event);
        return Optional.of(event);
    }

    private long incrementConnections(UUID boardId, UUID userId) {
        Long result = hashOps().increment(connectionsKey(boardId), userId.toString(), 1L);
        return result == null ? 1L : result;
    }

    private long decrementConnections(UUID boardId, UUID userId) {
        Long result = hashOps().increment(connectionsKey(boardId), userId.toString(), -1L);
        long remaining = result == null ? 0L : result;
        if (remaining <= 0) {
            hashOps().delete(connectionsKey(boardId), userId.toString());
        }
        return remaining;
    }

    private long incrementRoomCount(UUID boardId) {
        Long result = redisTemplate.opsForValue().increment(countKey(boardId));
        return result == null ? 1L : result;
    }

    private long decrementRoomCount(UUID boardId) {
        Long result = redisTemplate.opsForValue().decrement(countKey(boardId));
        return result == null ? 0L : result;
    }

    private boolean roomExists(UUID boardId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(countKey(boardId)));
    }

    private VideoParticipant readParticipant(UUID boardId, UUID userId) {
        String raw = hashOps().get(participantsKey(boardId), userId.toString());
        return raw == null ? null : readJson(raw, boardId, userId);
    }

    private List<VideoParticipant> currentRoster(UUID boardId) {
        Map<String, String> raw = hashOps().entries(participantsKey(boardId));
        List<VideoParticipant> participants = new ArrayList<>(raw.size());
        raw.forEach((userId, json) -> {
            VideoParticipant participant = readJson(json, boardId, userId);
            if (participant != null) {
                participants.add(participant);
            }
        });
        participants.sort(Comparator.comparing(VideoParticipant::joinedAt));
        return participants;
    }

    private void deleteRoomKeys(UUID boardId) {
        redisTemplate.delete(List.of(
                participantsKey(boardId), connectionsKey(boardId), countKey(boardId), creatorKey(boardId)));
    }

    private VideoRoomEvent event(VideoRoomEventType type, UUID boardId, VideoParticipant participant, List<VideoParticipant> participants) {
        return new VideoRoomEvent(type, boardId, participant, participants, Instant.now().toEpochMilli());
    }

    private void publish(VideoRoomEvent event) {
        messagingTemplate.convertAndSend("/topic/boards/" + event.boardId() + "/video", event);
        videoEventBroadcaster.broadcast(event);
    }

    private void sendPrivateState(UUID boardId, String principalName, VideoRoomEvent event) {
        messagingTemplate.convertAndSendToUser(
                principalName, "/queue/boards/" + boardId + "/video/state", event);
    }

    private HashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    private String writeJson(VideoParticipant participant) {
        try {
            return objectMapper.writeValueAsString(participant);
        } catch (Exception ex) {
            // A three-field record with no cycles; this is not a reachable failure in
            // practice, but propagating rather than swallowing keeps the same "do not
            // pretend it succeeded" rule as the rest of this class.
            throw new IllegalStateException("Failed to serialize video participant", ex);
        }
    }

    private VideoParticipant readJson(String json, UUID boardId, Object userId) {
        try {
            return objectMapper.readValue(json, VideoParticipant.class);
        } catch (Exception ex) {
            log.error("Skipping unreadable video participant record for user {} on board {}", userId, boardId, ex);
            return null;
        }
    }

    private static String participantsKey(UUID boardId) {
        return "video:board:" + boardId + ":participants";
    }

    private static String connectionsKey(UUID boardId) {
        return "video:board:" + boardId + ":connections";
    }

    private static String countKey(UUID boardId) {
        return "video:board:" + boardId + ":count";
    }

    private static String creatorKey(UUID boardId) {
        return "video:board:" + boardId + ":creator";
    }
}
