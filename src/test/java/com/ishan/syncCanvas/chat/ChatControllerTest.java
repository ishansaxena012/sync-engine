package com.ishan.syncCanvas.chat;

import com.ishan.syncCanvas.chat.dto.ChatMessageResponse;
import com.ishan.syncCanvas.chat.dto.ChatSendRequest;
import com.ishan.syncCanvas.chat.exception.InvalidChatMessageException;
import com.ishan.syncCanvas.chat.service.ChatService;
import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.websocket.controller.ChatController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatController controller() {
        return new ChatController(chatService, messagingTemplate);
    }

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal = UserPrincipal.create(
            User.builder().id(userId).name("Ishan").email("ishan@example.com").build());

    @Test
    void authenticatedUserMessageIsForwardedWithTheSessionIdentity() {
        controller().sendMessage(boardId, new ChatSendRequest("Hello everyone!"), principal);

        // Board from the destination, sender from the session, text from the body.
        verify(chatService).post(boardId, principal, "Hello everyone!");
    }

    @Test
    void aBodyThatTriesToCarryIdentityCannotReachTheService() {
        // ChatSendRequest exposes nothing but `message`, so the only value the
        // controller can pass through is the text itself.
        controller().sendMessage(boardId, new ChatSendRequest("hi"), principal);

        ArgumentCaptor<UserPrincipal> sender = ArgumentCaptor.forClass(UserPrincipal.class);
        verify(chatService).post(eq(boardId), sender.capture(), eq("hi"));
        assertThat(sender.getValue().getId()).isEqualTo(userId);
    }

    @Test
    void missingBodyIsPassedAsNullForTheServiceToReject() {
        controller().sendMessage(boardId, null, principal);

        verify(chatService).post(boardId, principal, null);
    }

    @Test
    void nonUserPrincipalNeverReachesPersistence() {
        Principal anonymous = () -> "someone";
        ChatController controller = controller();
        ChatSendRequest request = new ChatSendRequest("hi");

        assertThatThrownBy(() -> controller.sendMessage(boardId, request, anonymous))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(chatService);
    }

    @Test
    void unauthorizedBoardAccessPropagatesToTheErrorHandler() {
        doThrow(new BoardAccessDeniedException("You do not have access to board " + boardId))
                .when(chatService).post(eq(boardId), any(), anyString());
        ChatController controller = controller();
        ChatSendRequest request = new ChatSendRequest("let me in");

        assertThatThrownBy(() -> controller.sendMessage(boardId, request, principal))
                .isInstanceOf(BoardAccessDeniedException.class);
    }

    @Test
    void sendFailureIsReportedToTheSenderAloneAndNotBroadcast() {
        controller().handleException(
                new InvalidChatMessageException("Message must not be blank"), boardId, principal);

        ArgumentCaptor<OperationErrorResponse> error =
                ArgumentCaptor.forClass(OperationErrorResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()),
                eq("/queue/boards/" + boardId + "/chat/errors"),
                error.capture());

        assertThat(error.getValue().type()).isEqualTo("CHAT_ERROR");
        assertThat(error.getValue().message()).contains("blank");
        assertThat(error.getValue().timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void errorsAreNotSentOnTheBoardWideChatOrOperationTopics() {
        controller().handleException(new InvalidChatMessageException("nope"), boardId, principal);

        // A rejected message is the sender's business only — everyone else never saw it.
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(eq("/topic/boards/" + boardId + "/chat"), any(Object.class));
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(eq("/topic/boards/" + boardId), any(Object.class));
    }

    @Test
    void errorWithNoPrincipalIsDroppedRatherThanSentToEveryone() {
        controller().handleException(new IllegalStateException("unauthenticated"), boardId, null);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void controllerReturnsNothingItselfSinceDeliveryIsViaTheTopic() {
        ChatMessageResponse canonical = new ChatMessageResponse(
                UUID.randomUUID(), boardId, userId, "Ishan", "hi", Instant.now());
        when(chatService.post(boardId, principal, "hi")).thenReturn(canonical);

        // The sender receives the canonical message on the same topic as everyone else;
        // the handler publishes no separate sender-only reply.
        controller().sendMessage(boardId, new ChatSendRequest("hi"), principal);

        verifyNoInteractions(messagingTemplate);
    }
}
