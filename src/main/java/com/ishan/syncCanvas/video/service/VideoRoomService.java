package com.ishan.syncCanvas.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.video.dto.VideoRoomBroadcastEvent;
import com.ishan.syncCanvas.video.dto.VideoRoomEvent;
import com.ishan.syncCanvas.video.dto.VideoRoomEventType;
import com.ishan.syncCanvas.video.dto.VideoRoomResponse;
import com.ishan.syncCanvas.video.dto.VideoRosterResponse;
import com.ishan.syncCanvas.video.dto.VideoSignalingSession;
import com.ishan.syncCanvas.video.exception.VideoRoomAccessDeniedException;
import com.ishan.syncCanvas.video.exception.VideoRoomFullException;
import com.ishan.syncCanvas.video.exception.VideoRoomNotFoundException;
import com.ishan.syncCanvas.video.model.VideoParticipant;
import com.ishan.syncCanvas.video.publisher.VideoEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
 *   <li>{@code sessions} — Hash, {@code userId -> signalingSessionId} (a fresh
 *       server-generated UUID minted on every single join call). This is the
 *       "generation/fencing token" {@code VideoSignalService} checks each signal
 *       against so a reconnected participant's old, superseded connection can no
 *       longer signal. See the join() Javadoc for the multi-tab trade-off this
 *       implies.</li>
 * </ul>
 *
 * <p>Every join/start reply — not just the public broadcast — is also sent privately to
 * the caller on {@code /user/queue/boards/{boardId}/video/roster}, carrying the full
 * current roster, and a second private reply on {@code
 * /user/queue/boards/{boardId}/video/session} carrying that connection's fresh
 * signaling session id. A repeat join from a second tab produces no public broadcast
 * (nothing changed for anyone else), so without the private roster reply that tab would
 * have no way to learn who is already in the call.
 *
 * <p>All five keys share one TTL, refreshed on every legitimate join/leave, as a safety
 * net against a room orphaned by a crashed instance that never fires a disconnect event
 * (normal END/drain-to-zero already deletes the keys immediately and needs no TTL).
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

    @Value("${video.room.max-participants:4}")
    private int maxParticipants;

    /** Orphan safety net only — a room that ends normally is deleted immediately, well before this. */
    @Value("${video.room.ttl-seconds:21600}")
    private long roomTtlSeconds;

    // ---------------------------------------------------------------- public API

    /**
     * Starts or joins a board's video call — the two are the same operation. If the
     * room already has a participant, this adds the caller to it rather than creating
     * a second one; if it is empty or missing, this call creates it and the caller
     * becomes its recorded creator. Rejected with {@link VideoRoomFullException} if the
     * room is already at {@code video.room.max-participants} distinct participants —
     * additional tabs of an already-active participant are exempt, since they do not
     * grow the mesh.
     *
     * <p>Every call — a brand new join, an additional tab, or a rejoin after
     * disconnect — mints a fresh signaling session id for this connection and makes it
     * the sole authoritative one for this user in this room, sent back privately on
     * {@code /user/queue/boards/{boardId}/video/session}. This is a deliberate
     * simplification: room <em>membership</em> (whether the user is in the call at all)
     * is entirely unaffected and still governed by the connection counter above — a
     * second tab does not remove the first from the roster. Only <em>signaling
     * authority</em> narrows to whichever connection joined most recently; an older tab
     * that is still technically connected can no longer send signals once superseded.
     * For the "2-4 participants, no SFU" scope this targets, a single user running two
     * simultaneous WebRTC legs into the same mesh is already an unsupported edge case,
     * so this trade-off is accepted rather than building per-tab signaling routing.
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
            long roomCount = incrementRoomCount(boardId);
            if (roomCount > maxParticipants) {
                // Roll back both increments -- this join must not count as having
                // happened at all; no participant record is ever written for it.
                decrementRoomCount(boardId);
                decrementConnections(boardId, userId);
                log.warn("VIDEO_ROOM_FULL boardId={} userId={} maxParticipants={}", boardId, userId, maxParticipants);
                throw new VideoRoomFullException(boardId, maxParticipants);
            }
            VideoParticipant participant = new VideoParticipant(userId, user.getDisplayName(), Instant.now(), true, true);
            hashOps().put(participantsKey(boardId), userId.toString(), writeJson(participant));
            VideoRoomEventType type = roomCount == 1 ? VideoRoomEventType.ROOM_STARTED : VideoRoomEventType.PARTICIPANT_JOINED;
            state = event(type, boardId, participant, currentRoster(boardId));
            refreshRoomTtl(boardId);
            publish(state);
            log.info("{} boardId={} userId={}",
                    type == VideoRoomEventType.ROOM_STARTED ? "VIDEO_ROOM_STARTED" : "VIDEO_PARTICIPANT_JOINED",
                    boardId, userId);
        } else {
            // Another tab/session for this same user already holds this room open —
            // nothing changed for anyone else, so nothing is broadcast.
            state = event(VideoRoomEventType.ROOM_STATE, boardId, null, currentRoster(boardId));
            refreshRoomTtl(boardId);
        }
        String signalingSessionId = rotateSignalingSession(boardId, userId);
        sendRoster(boardId, user.getName());
        sendPrivateSignalingSession(boardId, user.getName(), signalingSessionId);
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
     * True if {@code signalingSessionId} is still the current one for this user in this
     * board's room — i.e. this is still the most-recently-joined connection for them,
     * not one superseded by a later reconnect/second tab. Used by
     * {@code VideoSignalService} to reject stale signaling from a superseded
     * connection; see {@link #join} for the "latest connection wins" trade-off this
     * reflects.
     */
    public boolean isCurrentSignalingSession(UUID boardId, UUID userId, String signalingSessionId) {
        String current = hashOps().get(sessionsKey(boardId), userId.toString());
        return current != null && current.equals(signalingSessionId);
    }

    /**
     * Updates the caller's own mic/camera flags and broadcasts {@code
     * PARTICIPANT_STATE_CHANGED} to the room. A no-op participant lookup (not an
     * exception) if the caller isn't currently an active participant would silently
     * discard a real toggle, so this rejects instead via {@link VideoRoomAccessDeniedException}
     * — a user can only ever update their own state, never anyone else's.
     */
    public void updateParticipantState(UUID boardId, UserPrincipal user, boolean isMicEnabled, boolean isCameraEnabled) {
        boardAccessGuard.assertAccessible(boardId, user.getId());
        UUID userId = user.getId();

        VideoParticipant existing = readParticipant(boardId, userId);
        if (existing == null) {
            throw new VideoRoomAccessDeniedException("You are not an active participant in this video call");
        }

        VideoParticipant updated = new VideoParticipant(
                userId, existing.userName(), existing.joinedAt(), isMicEnabled, isCameraEnabled);
        hashOps().put(participantsKey(boardId), userId.toString(), writeJson(updated));

        VideoRoomEvent stateChanged = event(VideoRoomEventType.PARTICIPANT_STATE_CHANGED, boardId, updated, currentRoster(boardId));
        publish(stateChanged);
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
            log.info("VIDEO_ROOM_ENDED boardId={} userId={}", boardId, userId);
        } else {
            event = event(VideoRoomEventType.PARTICIPANT_LEFT, boardId, leaving, currentRoster(boardId));
            refreshRoomTtl(boardId);
            log.info("VIDEO_PARTICIPANT_LEFT boardId={} userId={} remaining={}", boardId, userId, roomCount);
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
                participantsKey(boardId), connectionsKey(boardId), countKey(boardId), creatorKey(boardId),
                sessionsKey(boardId)));
    }

    /**
     * Server-driven TTL refresh, called only from join/leave (never from a raw client
     * request) — purely an orphan safety net for a room whose owning instance crashed
     * without ever firing a disconnect event. Normal teardown (explicit END, drain to
     * zero, board deletion) deletes these keys immediately, well before this would ever
     * matter.
     */
    private void refreshRoomTtl(UUID boardId) {
        Duration ttl = Duration.ofSeconds(roomTtlSeconds);
        redisTemplate.expire(participantsKey(boardId), ttl);
        redisTemplate.expire(connectionsKey(boardId), ttl);
        redisTemplate.expire(countKey(boardId), ttl);
        redisTemplate.expire(creatorKey(boardId), ttl);
        redisTemplate.expire(sessionsKey(boardId), ttl);
    }

    private String rotateSignalingSession(UUID boardId, UUID userId) {
        String signalingSessionId = UUID.randomUUID().toString();
        hashOps().put(sessionsKey(boardId), userId.toString(), signalingSessionId);
        return signalingSessionId;
    }

    private void sendPrivateSignalingSession(UUID boardId, String principalName, String signalingSessionId) {
        messagingTemplate.convertAndSendToUser(
                principalName, "/queue/boards/" + boardId + "/video/session",
                new VideoSignalingSession(signalingSessionId));
    }

    private VideoRoomEvent event(VideoRoomEventType type, UUID boardId, VideoParticipant participant, List<VideoParticipant> participants) {
        return new VideoRoomEvent(type, boardId, participant, participants, Instant.now().toEpochMilli());
    }

    private void publish(VideoRoomEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/boards/" + event.boardId() + "/video", VideoRoomBroadcastEvent.from(event));
        videoEventBroadcaster.broadcast(event);
    }

    private void sendRoster(UUID boardId, String principalName) {
        messagingTemplate.convertAndSendToUser(
                principalName, "/queue/boards/" + boardId + "/video/roster",
                new VideoRosterResponse(currentRoster(boardId)));
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

    private static String sessionsKey(UUID boardId) {
        return "video:board:" + boardId + ":sessions";
    }
}
