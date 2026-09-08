package com.ishan.syncCanvas.collaboration.cursor;

import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
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
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CursorPresenceListenerTest {

    @Mock
    private CursorService cursorService;

    private CursorPresenceListener listener;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new CursorPresenceListener(cursorService);
    }

    private UserPrincipal user() {
        User u = User.builder().id(userId).name("Ishan").email("ishan@example.com").build();
        return UserPrincipal.create(u);
    }

    private Message<byte[]> stompMessage(StompCommand command, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/boards/" + boardId + "/cursor");
        accessor.setUser(user());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribeSendsInitialStateForCursorTopic() {
        listener.handleSubscribe(new SessionSubscribeEvent(this, stompMessage(StompCommand.SUBSCRIBE, "session-1")));

        verify(cursorService).sendInitialState(boardId, userId.toString());
    }

    @Test
    void disconnectOfOnlySessionRemovesCursor() {
        listener.handleSubscribe(new SessionSubscribeEvent(this, stompMessage(StompCommand.SUBSCRIBE, "session-1")));

        listener.handleDisconnect(new SessionDisconnectEvent(
                this, stompMessage(StompCommand.DISCONNECT, "session-1"), "session-1", CloseStatus.NORMAL));

        verify(cursorService).removeCursor(boardId, userId);
    }

    @Test
    void disconnectOfOneOfTwoSessionsDoesNotRemoveCursorUntilTheLastOneGoes() {
        listener.handleSubscribe(new SessionSubscribeEvent(this, stompMessage(StompCommand.SUBSCRIBE, "session-1")));
        listener.handleSubscribe(new SessionSubscribeEvent(this, stompMessage(StompCommand.SUBSCRIBE, "session-2")));

        listener.handleDisconnect(new SessionDisconnectEvent(
                this, stompMessage(StompCommand.DISCONNECT, "session-1"), "session-1", CloseStatus.NORMAL));

        verify(cursorService, never()).removeCursor(boardId, userId);

        listener.handleDisconnect(new SessionDisconnectEvent(
                this, stompMessage(StompCommand.DISCONNECT, "session-2"), "session-2", CloseStatus.NORMAL));

        verify(cursorService).removeCursor(boardId, userId);
    }

    @Test
    void unsubscribeRemovesCursorJustLikeDisconnect() {
        listener.handleSubscribe(new SessionSubscribeEvent(this, stompMessage(StompCommand.SUBSCRIBE, "session-1")));

        listener.handleUnsubscribe(new SessionUnsubscribeEvent(
                this, stompMessage(StompCommand.UNSUBSCRIBE, "session-1")));

        verify(cursorService).removeCursor(boardId, userId);
    }
}
