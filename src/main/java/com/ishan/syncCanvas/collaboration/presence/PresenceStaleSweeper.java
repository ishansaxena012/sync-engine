package com.ishan.syncCanvas.collaboration.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two jobs, both driven off Redis TTL rather than relying solely on clean
 * disconnects/heartbeats:
 * <ol>
 *   <li>Detect ONLINE→IDLE transitions (no heartbeat for the idle threshold) and
 *       broadcast one PRESENCE_UPDATE for it — deduplicated locally so this instance
 *       doesn't repeat the same broadcast every tick.</li>
 *   <li>Detect presence data whose TTL lapsed without ever going through
 *       {@code PresenceService.releaseConnection} (e.g. the owning ECS instance died
 *       before it could decrement the connection counter) and finish the cleanup —
 *       removing the stale membership and broadcasting USER_LEFT — exactly mirroring
 *       {@code CursorStaleSweeper}'s backstop role for cursors.</li>
 * </ol>
 * A duplicate broadcast from another instance sweeping the same board is harmless —
 * idempotent for connected clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceStaleSweeper {

    private final PresenceSessionTracker sessionTracker;
    private final PresenceService presenceService;

    private final Map<String, PresenceStatus> lastBroadcastStatus = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 10_000)
    public void sweep() {
        for (UUID boardId : sessionTracker.getActiveBoardIds()) {
            for (String memberId : presenceService.getPresenceMembers(boardId)) {
                UUID userId;
                try {
                    userId = UUID.fromString(memberId);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                String dedupKey = boardId + ":" + userId;
                PresenceEvent current = presenceService.getCurrentParticipantState(boardId, userId);

                if (current == null) {
                    // Data TTL lapsed without a clean release (e.g. instance died mid-session).
                    lastBroadcastStatus.remove(dedupKey);
                    log.debug("Presence for user {} on board {} expired without release; cleaning up", userId, boardId);
                    presenceService.removePresence(boardId, userId);
                    continue;
                }

                if (current.status() == PresenceStatus.IDLE) {
                    if (lastBroadcastStatus.put(dedupKey, PresenceStatus.IDLE) != PresenceStatus.IDLE) {
                        presenceService.broadcastIdleTransition(boardId, userId, current.displayName());
                    }
                } else {
                    lastBroadcastStatus.remove(dedupKey);
                }
            }
        }
    }
}
