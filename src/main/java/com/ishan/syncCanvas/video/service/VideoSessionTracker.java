package com.ishan.syncCanvas.video.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure local (per-instance) bookkeeping of which STOMP session is participating in
 * which board's video call, for which user — mirrors
 * {@code PresenceSessionTracker}, deliberately as a separate instance with no
 * dependency on it or on {@link VideoRoomService}, so video membership and board
 * presence stay independent systems that merely follow the same shape.
 *
 * <p>A STOMP session only ever exists on the instance holding its WebSocket
 * connection, so this is instance-local by necessity; the cross-instance "was this the
 * user's last connection anywhere" decision is made in {@link VideoRoomService} via
 * the Redis {@code connections} hash, not here.
 */
@Component
public class VideoSessionTracker {

    private final Map<String, BoardUser> sessionBoardUser = new ConcurrentHashMap<>();

    /** @return false if this exact session was already registered (idempotent duplicate join). */
    public boolean register(String sessionId, UUID boardId, UUID userId) {
        return sessionBoardUser.putIfAbsent(sessionId, new BoardUser(boardId, userId)) == null;
    }

    /** @return the (boardId, userId) this session's video membership belonged to, or empty if not tracked. */
    public Optional<BoardUser> unregister(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionBoardUser.remove(sessionId));
    }

    public record BoardUser(UUID boardId, UUID userId) {
    }
}
