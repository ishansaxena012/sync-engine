package com.ishan.syncCanvas.collaboration.cursor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks which STOMP sessions are subscribed to which board's cursor topic, purely for
 * cursor presence:
 * <ul>
 *   <li>sends a newly-subscribed client the board's current cursor snapshot, and</li>
 *   <li>removes a user's cursor (publishing CURSOR_LEAVE) only once their <em>last</em>
 *       active session/subscription for that board disappears, so a user with two open
 *       tabs on the same board doesn't flicker away when one tab closes.</li>
 * </ul>
 * Deliberately separate from {@link com.ishan.syncCanvas.collaboration.session.BoardSessionManager} —
 * cursor presence has nothing to do with the persistent canvas-object session or its
 * locking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CursorPresenceListener {

    private static final Pattern CURSOR_TOPIC = Pattern.compile("^/topic/boards/([^/]+)/cursor$");

    private final CursorService cursorService;

    /** A session subscribes to at most one board's cursor topic at a time. */
    private final Map<String, UUID> sessionBoard = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Set<String>>> boardUserSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID boardId = extractBoardId(accessor.getDestination());
        if (boardId == null) {
            return;
        }

        Principal principal = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (principal == null || sessionId == null) {
            return;
        }

        UUID userId = UUID.fromString(principal.getName());

        sessionBoard.put(sessionId, boardId);
        boardUserSessions
                .computeIfAbsent(boardId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        cursorService.sendInitialState(boardId, principal.getName());
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        removeSession(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        removeSession(event.getSessionId());
    }

    /** Boards this instance currently has at least one local cursor subscriber for. */
    public Set<UUID> getActiveBoardIds() {
        return Set.copyOf(boardUserSessions.keySet());
    }

    private void removeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        UUID boardId = sessionBoard.remove(sessionId);
        if (boardId == null) {
            return;
        }

        Map<UUID, Set<String>> userSessions = boardUserSessions.get(boardId);
        if (userSessions == null) {
            return;
        }

        userSessions.forEach((userId, sessions) -> {
            if (sessions.remove(sessionId) && sessions.isEmpty()) {
                userSessions.remove(userId);
                cursorService.removeCursor(boardId, userId);
            }
        });

        if (userSessions.isEmpty()) {
            boardUserSessions.remove(boardId);
        }
    }

    private static UUID extractBoardId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = CURSOR_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
