package com.ishan.syncCanvas.collaboration.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.cursor.CursorColorPalette;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ephemeral board-presence state. Like {@code CursorService}, this has no dependency on
 * BoardSession, the persistence scheduler, or any JPA repository — presence never
 * touches Postgres and never enters the canvas-object operation pipeline.
 *
 * <p>Owns all Redis state for presence: the per-participant record (with TTL), the
 * per-board membership set, and the per-(board,user) cross-instance connection counter
 * that makes multi-tab handling correct even when a user's tabs land on different ECS
 * instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceEventBroadcaster presenceEventBroadcaster;
    private final BoardAccessGuard boardAccessGuard;

    @Value("${app.collaboration.presence-ttl-seconds:90}")
    private long presenceTtlSeconds;

    @Value("${app.collaboration.presence-idle-threshold-seconds:30}")
    private long idleThresholdSeconds;

    public boolean checkAccessible(UUID boardId, UUID userId) {
        try {
            boardAccessGuard.assertAccessible(boardId, userId);
            return true;
        } catch (BoardAccessDeniedException ex) {
            log.warn("Rejected presence request for user {} on board {}: {}", userId, boardId, ex.getMessage());
            return false;
        }
    }

    /**
     * Increments the cluster-wide connection count for (boardId, userId).
     *
     * @return true if this was the first connection anywhere for this user on this
     *         board (a genuine join); false if another tab/instance already holds one.
     */
    public boolean recordConnection(UUID boardId, UUID userId) {
        String key = connectionsKey(boardId, userId);
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofSeconds(presenceTtlSeconds));
        return count != null && count == 1L;
    }

    /** Decrements the connection count; removes presence (and broadcasts USER_LEFT) once it hits zero. */
    public void releaseConnection(UUID boardId, UUID userId) {
        String key = connectionsKey(boardId, userId);
        Long remaining = redisTemplate.opsForValue().decrement(key);
        if (remaining == null || remaining <= 0) {
            redisTemplate.delete(key);
            removePresence(boardId, userId);
        }
    }

    public void completeJoin(UUID boardId, UserPrincipal user, boolean broadcastJoin) {
        storePresence(boardId, user.getId(), user.getDisplayName());

        if (broadcastJoin) {
            broadcast(boardId, PresenceEventType.USER_JOINED, user.getId(), user.getDisplayName(), PresenceStatus.ONLINE);
        }

        // Every joining session gets its own snapshot, even if the underlying user was
        // already present via another tab and no USER_JOINED was broadcast for them.
        sendInitialState(boardId, user.getName());
    }

    public void heartbeat(UUID boardId, UserPrincipal user) {
        if (!checkAccessible(boardId, user.getId())) {
            return;
        }

        PresenceRecord existing = readPresenceRecord(boardId, user.getId());
        boolean wasIdle = existing != null && deriveStatus(existing.lastSeen()) == PresenceStatus.IDLE;

        storePresence(boardId, user.getId(), user.getDisplayName());

        if (wasIdle) {
            broadcast(boardId, PresenceEventType.PRESENCE_UPDATE, user.getId(), user.getDisplayName(), PresenceStatus.ONLINE);
        }
    }

    /** Clears a user's presence for a board and tells everyone (local + other instances) they left. */
    public void removePresence(UUID boardId, UUID userId) {
        String displayName = null;
        try {
            PresenceRecord existing = readPresenceRecord(boardId, userId);
            displayName = existing != null ? existing.displayName() : null;
            redisTemplate.delete(presenceKey(boardId, userId));
            redisTemplate.opsForSet().remove(membersKey(boardId), userId.toString());
        } catch (Exception ex) {
            log.error("Failed to clear presence state for user {} on board {}", userId, boardId, ex);
        }

        broadcast(boardId, PresenceEventType.USER_LEFT, userId, displayName, null);
    }

    /**
     * Removes every presence key for a board — the per-participant records, their
     * connection counters, and the membership set — with no USER_LEFT broadcast, since
     * this is only called when the board itself is being deleted.
     */
    public void clearBoardState(UUID boardId) {
        try {
            for (String memberId : getPresenceMembers(boardId)) {
                UUID userId = parseUuid(memberId);
                if (userId != null) {
                    redisTemplate.delete(presenceKey(boardId, userId));
                    redisTemplate.delete(connectionsKey(boardId, userId));
                }
            }
            redisTemplate.delete(membersKey(boardId));
        } catch (Exception ex) {
            log.error("Failed to clear presence state for board {}", boardId, ex);
        }
    }

    /** Current live participants for a board, skipping any membership entry whose TTL already lapsed. */
    public List<PresenceEvent> getActiveParticipants(UUID boardId) {
        List<PresenceEvent> participants = new ArrayList<>();
        for (String memberId : getPresenceMembers(boardId)) {
            UUID userId = parseUuid(memberId);
            if (userId == null) {
                continue;
            }
            PresenceRecord record = readPresenceRecord(boardId, userId);
            if (record == null) {
                continue; // TTL already lapsed; PresenceStaleSweeper will retire the membership
            }
            participants.add(toEvent(PresenceEventType.PRESENCE_UPDATE, userId, record.displayName(), deriveStatus(record.lastSeen())));
        }
        return participants;
    }

    public Set<String> getPresenceMembers(UUID boardId) {
        Set<String> members = redisTemplate.opsForSet().members(membersKey(boardId));
        return members == null ? Set.of() : members;
    }

    /** Current derived state for one participant, or null if their presence data has already expired. */
    public PresenceEvent getCurrentParticipantState(UUID boardId, UUID userId) {
        PresenceRecord record = readPresenceRecord(boardId, userId);
        if (record == null) {
            return null;
        }
        return toEvent(PresenceEventType.PRESENCE_UPDATE, userId, record.displayName(), deriveStatus(record.lastSeen()));
    }

    public void broadcastIdleTransition(UUID boardId, UUID userId, String displayName) {
        broadcast(boardId, PresenceEventType.PRESENCE_UPDATE, userId, displayName, PresenceStatus.IDLE);
    }

    public void sendInitialState(UUID boardId, String principalName) {
        messagingTemplate.convertAndSendToUser(
                principalName,
                "/queue/boards/" + boardId + "/presence/initial",
                new PresenceInitialStateEvent(boardId, getActiveParticipants(boardId)));
    }

    private void storePresence(UUID boardId, UUID userId, String displayName) {
        try {
            PresenceRecord record = new PresenceRecord(userId, displayName, Instant.now().toEpochMilli());
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(presenceKey(boardId, userId), json, Duration.ofSeconds(presenceTtlSeconds));
            redisTemplate.opsForSet().add(membersKey(boardId), userId.toString());
        } catch (Exception ex) {
            log.error("Failed to store presence state for board {}", boardId, ex);
        }
    }

    private PresenceRecord readPresenceRecord(UUID boardId, UUID userId) {
        try {
            String json = redisTemplate.opsForValue().get(presenceKey(boardId, userId));
            return json == null ? null : objectMapper.readValue(json, PresenceRecord.class);
        } catch (Exception ex) {
            log.error("Failed to read presence state for user {} on board {}", userId, boardId, ex);
            return null;
        }
    }

    private PresenceStatus deriveStatus(long lastSeenEpochMillis) {
        Duration since = Duration.between(Instant.ofEpochMilli(lastSeenEpochMillis), Instant.now());
        return since.getSeconds() > idleThresholdSeconds ? PresenceStatus.IDLE : PresenceStatus.ONLINE;
    }

    private void broadcast(UUID boardId, PresenceEventType type, UUID userId, String displayName, PresenceStatus status) {
        PresenceEvent event = toEvent(type, userId, displayName, status);
        publishLocally(boardId, event);
        presenceEventBroadcaster.broadcast(boardId, event);
    }

    private PresenceEvent toEvent(PresenceEventType type, UUID userId, String displayName, PresenceStatus status) {
        String color = displayName == null ? null : CursorColorPalette.colorFor(userId);
        return new PresenceEvent(type, userId, displayName, color, status, Instant.now().toEpochMilli());
    }

    private void publishLocally(UUID boardId, PresenceEvent event) {
        messagingTemplate.convertAndSend("/topic/boards/" + boardId + "/presence", event);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String presenceKey(UUID boardId, UUID userId) {
        return "presence:board:" + boardId + ":" + userId;
    }

    private static String membersKey(UUID boardId) {
        return "presence:board:" + boardId + ":members";
    }

    private static String connectionsKey(UUID boardId, UUID userId) {
        return "presence:board:" + boardId + ":" + userId + ":connections";
    }
}
