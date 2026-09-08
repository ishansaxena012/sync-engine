package com.ishan.syncCanvas.collaboration.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Ephemeral cursor-position sync. Deliberately has no dependency on BoardSession, the
 * persistence scheduler, or any JPA repository — a cursor update never touches
 * Postgres and never enters the canvas-object operation pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CursorService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final CursorEventBroadcaster cursorEventBroadcaster;
    private final BoardAccessGuard boardAccessGuard;

    @Value("${app.collaboration.cursor-ttl-seconds:15}")
    private long cursorTtlSeconds;

    public void updateCursor(UUID boardId, UserPrincipal user, CursorUpdateRequest request) {
        if (request == null || !isFinite(request.x()) || !isFinite(request.y())) {
            log.warn("Dropping cursor update with invalid coordinates from user {} on board {}", user.getId(), boardId);
            return;
        }

        try {
            boardAccessGuard.assertAccessible(boardId, user.getId());
        } catch (BoardAccessDeniedException ex) {
            // Ephemeral, high-frequency channel — reject by dropping rather than
            // notifying the client, so an unauthorized cursor stream can't be used to
            // flood the board's error channel or affect persistent canvas operations.
            log.warn("Rejected cursor update for user {} on board {}: {}", user.getId(), boardId, ex.getMessage());
            return;
        }

        CursorEvent event = new CursorEvent(
                CursorEventType.CURSOR_MOVE,
                user.getId(),
                user.getDisplayName(),
                CursorColorPalette.colorFor(user.getId()),
                request.x(),
                request.y(),
                Instant.now().toEpochMilli());

        storeCursor(boardId, event);
        publishLocally(boardId, event);
        cursorEventBroadcaster.broadcast(boardId, event);
    }

    /** Clears a user's cursor for a board and tells everyone (local + other instances) it's gone. */
    public void removeCursor(UUID boardId, UUID userId) {
        try {
            redisTemplate.delete(cursorKey(boardId, userId));
            redisTemplate.opsForSet().remove(membersKey(boardId), userId.toString());
        } catch (Exception ex) {
            log.error("Failed to clear cursor state for user {} on board {}", userId, boardId, ex);
        }

        CursorEvent leaveEvent = new CursorEvent(
                CursorEventType.CURSOR_LEAVE, userId, null, null, null, null, Instant.now().toEpochMilli());

        publishLocally(boardId, leaveEvent);
        cursorEventBroadcaster.broadcast(boardId, leaveEvent);
    }

    /** Current live cursors for a board, skipping any membership entry whose TTL already lapsed. */
    public List<CursorEvent> getActiveCursors(UUID boardId) {
        List<CursorEvent> cursors = new ArrayList<>();
        try {
            for (String memberId : getCursorMembers(boardId)) {
                String json = redisTemplate.opsForValue().get(cursorKey(boardId, UUID.fromString(memberId)));
                if (json == null) {
                    continue; // TTL already lapsed; CursorStaleSweeper will retire the membership
                }
                cursors.add(objectMapper.readValue(json, CursorEvent.class));
            }
        } catch (Exception ex) {
            log.error("Failed to read active cursors for board {}", boardId, ex);
        }
        return cursors;
    }

    public Set<String> getCursorMembers(UUID boardId) {
        Set<String> members = redisTemplate.opsForSet().members(membersKey(boardId));
        return members == null ? Set.of() : members;
    }

    public boolean isCursorLive(UUID boardId, UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cursorKey(boardId, userId)));
    }

    public void sendInitialState(UUID boardId, String principalName) {
        messagingTemplate.convertAndSendToUser(
                principalName,
                "/queue/boards/" + boardId + "/cursor/initial",
                new CursorInitialStateEvent(boardId, getActiveCursors(boardId)));
    }

    private void storeCursor(UUID boardId, CursorEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.opsForValue().set(cursorKey(boardId, event.userId()), json, Duration.ofSeconds(cursorTtlSeconds));
            redisTemplate.opsForSet().add(membersKey(boardId), event.userId().toString());
        } catch (Exception ex) {
            log.error("Failed to store cursor state for board {}", boardId, ex);
        }
    }

    private void publishLocally(UUID boardId, CursorEvent event) {
        messagingTemplate.convertAndSend("/topic/boards/" + boardId + "/cursor", event);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String cursorKey(UUID boardId, UUID userId) {
        return "cursor:board:" + boardId + ":" + userId;
    }

    private static String membersKey(UUID boardId) {
        return "cursor:board:" + boardId + ":members";
    }
}
