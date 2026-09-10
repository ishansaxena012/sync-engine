package com.ishan.syncCanvas.collaboration.presence;

import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.websocket.controller.PresenceController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceControllerTest {

    @Mock
    private PresenceService presenceService;
    @Mock
    private PresenceSessionTracker sessionTracker;

    private PresenceController controller;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String sessionId = "session-1";

    // UserPrincipal has no equals()/hashCode() override, so Mockito's verify() needs the
    // exact same instance used for both the call and the assertion — not a fresh one.
    private final UserPrincipal principal =
            UserPrincipal.create(User.builder().id(userId).name("Ishan").email("ishan@example.com").build());

    @BeforeEach
    void setUp() {
        controller = new PresenceController(presenceService, sessionTracker);
    }

    private UserPrincipal principal() {
        return principal;
    }

    @Test
    void joinRejectedWhenBoardNotAccessible() {
        when(presenceService.checkAccessible(boardId, userId)).thenReturn(false);

        controller.handlePresence(boardId, new PresenceRequest(PresenceRequestType.JOIN), principal(), sessionId);

        verifyNoInteractions(sessionTracker);
    }

    @Test
    void genuineFirstJoinBroadcasts() {
        when(presenceService.checkAccessible(boardId, userId)).thenReturn(true);
        when(sessionTracker.register(sessionId, boardId, userId)).thenReturn(true);
        when(presenceService.recordConnection(boardId, userId)).thenReturn(true);

        controller.handlePresence(boardId, new PresenceRequest(PresenceRequestType.JOIN), principal(), sessionId);

        verify(presenceService).completeJoin(boardId, principal(), true);
    }

    @Test
    void additionalTabJoinDoesNotRebroadcast() {
        when(presenceService.checkAccessible(boardId, userId)).thenReturn(true);
        when(sessionTracker.register(sessionId, boardId, userId)).thenReturn(true);
        when(presenceService.recordConnection(boardId, userId)).thenReturn(false);

        controller.handlePresence(boardId, new PresenceRequest(PresenceRequestType.JOIN), principal(), sessionId);

        verify(presenceService).completeJoin(boardId, principal(), false);
    }

    @Test
    void duplicateJoinFromSameSessionIsNoOp() {
        when(presenceService.checkAccessible(boardId, userId)).thenReturn(true);
        when(sessionTracker.register(sessionId, boardId, userId)).thenReturn(false);

        controller.handlePresence(boardId, new PresenceRequest(PresenceRequestType.JOIN), principal(), sessionId);

        verify(presenceService, never()).recordConnection(any(UUID.class), any(UUID.class));
        verify(presenceService, never()).completeJoin(any(UUID.class), any(UserPrincipal.class), anyBoolean());
    }

    @Test
    void heartbeatDelegatesDirectlyWithoutSessionTracker() {
        controller.handlePresence(boardId, new PresenceRequest(PresenceRequestType.HEARTBEAT), principal(), sessionId);

        verify(presenceService).heartbeat(boardId, principal());
        verifyNoInteractions(sessionTracker);
    }

    @Test
    void explicitLeaveReleasesConnectionWhenSessionWasTracked() {
        when(sessionTracker.unregister(sessionId)).thenReturn(Optional.of(new PresenceSessionTracker.BoardUser(boardId, userId)));

        controller.handlePresence(boardId, new PresenceRequest(PresenceRequestType.LEAVE), principal(), sessionId);

        verify(presenceService).releaseConnection(boardId, userId);
    }

    @Test
    void duplicateLeaveIsNoOp() {
        when(sessionTracker.unregister(sessionId)).thenReturn(Optional.empty());

        controller.handlePresence(boardId, new PresenceRequest(PresenceRequestType.LEAVE), principal(), sessionId);

        verifyNoInteractions(presenceService);
    }

    @Test
    void disconnectReleasesConnectionForTrackedSession() {
        when(sessionTracker.unregister(sessionId)).thenReturn(Optional.of(new PresenceSessionTracker.BoardUser(boardId, userId)));

        controller.handleDisconnect(new SessionDisconnectEvent(this, stompMessage(StompCommand.DISCONNECT), sessionId, CloseStatus.NORMAL));

        verify(presenceService).releaseConnection(boardId, userId);
    }

    @Test
    void unsubscribeReleasesConnectionForTrackedSession() {
        when(sessionTracker.unregister(sessionId)).thenReturn(Optional.of(new PresenceSessionTracker.BoardUser(boardId, userId)));

        controller.handleUnsubscribe(new SessionUnsubscribeEvent(this, stompMessage(StompCommand.UNSUBSCRIBE)));

        verify(presenceService).releaseConnection(boardId, userId);
    }

    private Message<byte[]> stompMessage(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
