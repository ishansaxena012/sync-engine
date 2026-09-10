package com.ishan.syncCanvas.collaboration.presence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Pure local (per-instance) bookkeeping of which STOMP session is presencing on which
 * board, for which user. Deliberately has no Redis access and no dependency on
 * {@link PresenceService} — it only answers "what did this session belong to?" so that
 * {@code PresenceController} (which depends on both this and the service) can decide
 * what to do, without the two components depending on each other.
 *
 * <p>Note this is instance-local by necessity — a STOMP session only ever exists on the
 * instance holding its WebSocket connection. The cross-instance "was this the user's
 * last connection anywhere" decision is made in {@link PresenceService} via a Redis
 * connection counter, not here.
 */
@Component
public class PresenceSessionTracker {

    private final Map<String, BoardUser> sessionBoardUser = new ConcurrentHashMap<>();

    /** @return false if this exact session was already registered (idempotent duplicate JOIN). */
    public boolean register(String sessionId, UUID boardId, UUID userId) {
        return sessionBoardUser.putIfAbsent(sessionId, new BoardUser(boardId, userId)) == null;
    }

    /** @return the (boardId, userId) this session belonged to, or empty if not tracked / already removed. */
    public Optional<BoardUser> unregister(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionBoardUser.remove(sessionId));
    }

    /** Boards this instance currently has at least one local presence session for. */
    public Set<UUID> getActiveBoardIds() {
        return sessionBoardUser.values().stream()
                .map(BoardUser::boardId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public record BoardUser(UUID boardId, UUID userId) {
    }
}
