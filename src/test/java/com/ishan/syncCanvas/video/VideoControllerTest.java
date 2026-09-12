package com.ishan.syncCanvas.video;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.video.controller.VideoController;
import com.ishan.syncCanvas.video.dto.VideoSignalRequest;
import com.ishan.syncCanvas.video.exception.VideoRoomAccessDeniedException;
import com.ishan.syncCanvas.video.service.VideoRoomService;
import com.ishan.syncCanvas.video.service.VideoSessionTracker;
import com.ishan.syncCanvas.video.service.VideoSignalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

    @Mock
    private VideoRoomService videoRoomService;
    @Mock
    private VideoSessionTracker sessionTracker;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private VideoSignalService videoSignalService;

    private VideoController controller;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String sessionId = "session-1";

    // UserPrincipal has no equals()/hashCode() override, so verify() needs the exact
    // same instance used for both the call and the assertion.
    private final UserPrincipal principal =
            UserPrincipal.create(User.builder().id(userId).name("Ishan").email("ishan@example.com").build());

    private VideoController controller() {
        return new VideoController(videoRoomService, sessionTracker, messagingTemplate, videoSignalService);
    }

    @Test
    void startRegistersTheSessionAndJoinsTheRoom() {
        controller().start(boardId, principal, sessionId);

        verify(sessionTracker).register(sessionId, boardId, userId);
        verify(videoRoomService).join(boardId, principal);
    }

    @Test
    void joinRegistersTheSessionAndJoinsTheRoom() {
        controller().join(boardId, principal, sessionId);

        verify(sessionTracker).register(sessionId, boardId, userId);
        verify(videoRoomService).join(boardId, principal);
    }

    @Test
    void leaveUnregistersTheSessionAndReleasesTheParticipant() {
        controller().leave(boardId, principal, sessionId);

        verify(sessionTracker).unregister(sessionId);
        verify(videoRoomService).leave(boardId, principal);
    }

    @Test
    void endDelegatesToTheServiceWithoutTouchingTheSessionTracker() {
        controller().end(boardId, principal);

        verify(videoRoomService).end(boardId, principal);
        verifyNoInteractions(sessionTracker);
    }

    @Test
    void signalDelegatesToTheSignalServiceWithTheAuthenticatedSender() {
        VideoSignalRequest request = new VideoSignalRequest(
                com.ishan.syncCanvas.video.dto.VideoSignalType.OFFER, UUID.randomUUID(), null);

        controller().signal(boardId, request, principal);

        verify(videoSignalService).relay(boardId, principal, request);
    }

    @Test
    void unauthenticatedSignalIsRejectedWithoutReachingTheService() {
        Principal anonymous = () -> "someone";
        VideoController controller = controller();
        VideoSignalRequest request = new VideoSignalRequest(
                com.ishan.syncCanvas.video.dto.VideoSignalType.OFFER, UUID.randomUUID(), null);

        assertThatThrownBy(() -> controller.signal(boardId, request, anonymous))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(videoSignalService);
    }

    @Test
    void unauthenticatedStartIsRejectedWithoutReachingTheService() {
        Principal anonymous = () -> "someone";
        VideoController controller = controller();

        assertThatThrownBy(() -> controller.start(boardId, anonymous, sessionId))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(videoRoomService, sessionTracker);
    }

    @Test
    void unauthenticatedEndIsRejected() {
        Principal anonymous = () -> "someone";
        VideoController controller = controller();

        assertThatThrownBy(() -> controller.end(boardId, anonymous))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(videoRoomService);
    }

    @Test
    void disconnectRemovesOnlyThatSessionsVideoMembership() {
        when(sessionTracker.unregister(sessionId))
                .thenReturn(Optional.of(new VideoSessionTracker.BoardUser(boardId, userId)));

        controller().handleDisconnect(disconnectEvent());

        verify(videoRoomService).removeParticipantOnDisconnect(boardId, userId);
    }

    @Test
    void disconnectForAnUntrackedSessionIsANoOp() {
        when(sessionTracker.unregister(sessionId)).thenReturn(Optional.empty());

        controller().handleDisconnect(disconnectEvent());

        verifyNoInteractions(videoRoomService);
    }

    @Test
    void disconnectDoesNotTouchBoardPresenceOrChat() {
        // The video controller's disconnect handler only ever calls the video
        // service — board presence has its own independent disconnect handling in
        // PresenceController, and this test pins down that VideoController never
        // reaches into it.
        when(sessionTracker.unregister(sessionId))
                .thenReturn(Optional.of(new VideoSessionTracker.BoardUser(boardId, userId)));

        controller().handleDisconnect(disconnectEvent());

        verify(videoRoomService, never()).leave(any(), any());
        verify(videoRoomService).removeParticipantOnDisconnect(boardId, userId);
    }

    @Test
    void rejectedStartRepliesPrivatelyToTheSenderOnly() {
        controller().handleException(
                new BoardAccessDeniedException("no access"), boardId, principal);

        ArgumentCaptor<OperationErrorResponse> error = ArgumentCaptor.forClass(OperationErrorResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()), eq("/queue/boards/" + boardId + "/video/errors"), error.capture());
        assertThat(error.getValue().type()).isEqualTo("VIDEO_ERROR");
        assertThat(error.getValue().message()).contains("no access");
    }

    @Test
    void rejectedEndRepliesPrivatelyNotToEveryoneInTheCall() {
        controller().handleException(
                new VideoRoomAccessDeniedException("not the creator"), boardId, principal);

        verify(messagingTemplate, never())
                .convertAndSend(eq("/topic/boards/" + boardId + "/video"), any(Object.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()), eq("/queue/boards/" + boardId + "/video/errors"), any(OperationErrorResponse.class));
    }

    @Test
    void exceptionHandlerDropsSilentlyWhenThereIsNoPrincipal() {
        controller().handleException(new IllegalStateException("unauthenticated"), boardId, null);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void malformedOrUnexpectedFailuresAreCaughtWithoutPropagatingFurther() {
        doThrow(new RuntimeException("boom")).when(videoRoomService).join(boardId, principal);
        VideoController controller = controller();

        // The controller method itself still throws (Spring routes it to
        // @MessageExceptionHandler) — the point is that handleException, exercised
        // directly here, never itself throws for an arbitrary failure.
        assertThatThrownBy(() -> controller.start(boardId, principal, sessionId))
                .isInstanceOf(RuntimeException.class);

        controller.handleException(new RuntimeException("boom"), boardId, principal);
        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()), eq("/queue/boards/" + boardId + "/video/errors"), any(OperationErrorResponse.class));
    }

    private SessionDisconnectEvent disconnectEvent() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }
}
