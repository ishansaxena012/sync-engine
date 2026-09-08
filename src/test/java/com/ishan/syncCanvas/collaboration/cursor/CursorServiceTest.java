package com.ishan.syncCanvas.collaboration.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CursorServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private CursorEventBroadcaster cursorEventBroadcaster;
    @Mock
    private BoardAccessGuard boardAccessGuard;

    private CursorService cursorService;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);

        cursorService = new CursorService(
                redisTemplate,
                new ObjectMapper(),
                messagingTemplate,
                cursorEventBroadcaster,
                boardAccessGuard);
        ReflectionTestUtils.setField(cursorService, "cursorTtlSeconds", 15L);
    }

    private UserPrincipal principal() {
        User user = User.builder().id(userId).name("Ishan").email("ishan@example.com").build();
        return UserPrincipal.create(user);
    }

    @Test
    void validCursorUpdateIsStoredAndBroadcast() {
        cursorService.updateCursor(boardId, principal(), new CursorUpdateRequest(10.5, 20.5));

        verify(valueOperations).set(
                eq("cursor:board:" + boardId + ":" + userId),
                anyString(),
                eq(Duration.ofSeconds(15)));
        verify(setOperations).add("cursor:board:" + boardId + ":members", userId.toString());
        verify(messagingTemplate).convertAndSend(eq("/topic/boards/" + boardId + "/cursor"), any(CursorEvent.class));
        verify(cursorEventBroadcaster).broadcast(eq(boardId), any(CursorEvent.class));
    }

    @Test
    void nonFiniteCoordinatesAreRejected() {
        cursorService.updateCursor(boardId, principal(), new CursorUpdateRequest(Double.NaN, 1));

        verifyNoInteractions(valueOperations, messagingTemplate, cursorEventBroadcaster);
    }

    @Test
    void infiniteCoordinatesAreRejected() {
        cursorService.updateCursor(boardId, principal(), new CursorUpdateRequest(Double.POSITIVE_INFINITY, 1));

        verifyNoInteractions(valueOperations, messagingTemplate, cursorEventBroadcaster);
    }

    @Test
    void unauthorizedBoardAccessIsRejected() {
        doThrow(new BoardAccessDeniedException("denied"))
                .when(boardAccessGuard).assertAccessible(boardId, userId);

        cursorService.updateCursor(boardId, principal(), new CursorUpdateRequest(1, 2));

        verifyNoInteractions(valueOperations, messagingTemplate, cursorEventBroadcaster);
    }

    @Test
    void cursorEventCarriesAuthenticatedUserIdNotClientSuppliedValue() {
        // CursorUpdateRequest has no user-identity field at all — the event's userId
        // can only ever come from the authenticated principal, never the client.
        cursorService.updateCursor(boardId, principal(), new CursorUpdateRequest(1, 2));

        verify(cursorEventBroadcaster).broadcast(eq(boardId), argThat((CursorEvent e) -> e.userId().equals(userId)));
    }

    @Test
    void removeCursorDeletesRedisStateAndPublishesLeave() {
        cursorService.removeCursor(boardId, userId);

        verify(redisTemplate).delete("cursor:board:" + boardId + ":" + userId);
        verify(setOperations).remove("cursor:board:" + boardId + ":members", userId.toString());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/boards/" + boardId + "/cursor"),
                argThat((CursorEvent e) -> e.type() == CursorEventType.CURSOR_LEAVE && e.userId().equals(userId)));
        verify(cursorEventBroadcaster).broadcast(
                eq(boardId),
                argThat((CursorEvent e) -> e.type() == CursorEventType.CURSOR_LEAVE));
    }
}
