package com.ishan.syncCanvas.chat;

import com.ishan.syncCanvas.chat.dto.ChatMessageResponse;
import com.ishan.syncCanvas.chat.entity.ChatMessage;
import com.ishan.syncCanvas.chat.exception.InvalidChatMessageException;
import com.ishan.syncCanvas.chat.publisher.ChatEventBroadcaster;
import com.ishan.syncCanvas.chat.repository.ChatMessageRepository;
import com.ishan.syncCanvas.chat.service.ChatService;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BoardAccessGuard boardAccessGuard;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ChatEventBroadcaster chatEventBroadcaster;

    private ChatService chatService() {
        return new ChatService(chatMessageRepository, userRepository, boardAccessGuard,
                messagingTemplate, chatEventBroadcaster);
    }

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private UserPrincipal principal() {
        return UserPrincipal.create(
                User.builder().id(userId).name("Ishan").email("ishan@example.com").build());
    }

    /** Mirrors the repository: returns the entity it was handed. */
    private void echoSave() {
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------------------------------------------------------------- persistence

    @Test
    void validMessageIsPersistedAndBroadcastWithServerOwnedFields() {
        echoSave();

        ChatMessageResponse response = chatService().post(boardId, principal(), "Hello everyone!");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getBoardId()).isEqualTo(boardId);
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getMessage()).isEqualTo("Hello everyone!");
        assertThat(saved.getValue().getId()).isNotNull();
        assertThat(saved.getValue().getCreatedAt()).isNotNull();

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.userName()).isEqualTo("Ishan");
        assertThat(response.boardId()).isEqualTo(boardId);
    }

    @Test
    void surroundingWhitespaceIsTrimmedButUnicodeAndInteriorTextArePreserved() {
        echoSave();

        ChatMessageResponse response =
                chatService().post(boardId, principal(), "  héllo  🌍  wörld \n");

        assertThat(response.message()).isEqualTo("héllo  🌍  wörld");
    }

    // ---------------------------------------------------------------- validation

    @Test
    void blankMessageIsRejectedAndNothingIsPersistedOrPublished() {
        ChatService service = chatService();

        assertThatThrownBy(() -> service.post(boardId, principal(), "   \n  "))
                .isInstanceOf(InvalidChatMessageException.class)
                .hasMessageContaining("blank");

        verify(chatMessageRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate, chatEventBroadcaster);
    }

    @Test
    void nullMessageIsRejectedRatherThanStoredAsEmpty() {
        ChatService service = chatService();

        assertThatThrownBy(() -> service.post(boardId, principal(), null))
                .isInstanceOf(InvalidChatMessageException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void oversizedMessageIsRejectedOutrightRatherThanSilentlyTruncated() {
        ChatService service = chatService();
        String tooLong = "x".repeat(ChatMessage.MAX_LENGTH + 1);
        UserPrincipal sender = principal();

        assertThatThrownBy(() -> service.post(boardId, sender, tooLong))
                .isInstanceOf(InvalidChatMessageException.class)
                .hasMessageContaining(String.valueOf(ChatMessage.MAX_LENGTH));

        verify(chatMessageRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate, chatEventBroadcaster);
    }

    @Test
    void messageExactlyAtTheLimitIsAccepted() {
        echoSave();

        ChatMessageResponse response =
                chatService().post(boardId, principal(), "x".repeat(ChatMessage.MAX_LENGTH));

        assertThat(response.message()).hasSize(ChatMessage.MAX_LENGTH);
    }

    // ---------------------------------------------------------------- authorization

    @Test
    void userWithoutBoardAccessCannotSendAndNothingIsPersistedOrPublished() {
        doThrow(new BoardAccessDeniedException("no access"))
                .when(boardAccessGuard).assertAccessible(boardId, userId);
        ChatService service = chatService();
        UserPrincipal sender = principal();

        assertThatThrownBy(() -> service.post(boardId, sender, "let me in"))
                .isInstanceOf(BoardAccessDeniedException.class);

        verifyNoInteractions(chatMessageRepository, messagingTemplate, chatEventBroadcaster);
    }

    @Test
    void userWithoutBoardAccessCannotReadHistory() {
        doThrow(new BoardAccessDeniedException("no access"))
                .when(boardAccessGuard).assertAccessible(boardId, userId);
        ChatService service = chatService();
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> service.history(boardId, userId, pageable))
                .isInstanceOf(BoardAccessDeniedException.class);

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void senderIdentityComesFromTheSessionSoAPayloadCannotSpoofIt() {
        echoSave();
        UUID someoneElse = UUID.randomUUID();

        // The request DTO carries only text — there is no field a client could set to
        // claim another user's id. This asserts the stored row is the session's user.
        ChatMessageResponse response = chatService().post(boardId, principal(), "hi");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId).isNotEqualTo(someoneElse);
        assertThat(response.userId()).isEqualTo(userId);
        // Access is checked against the session user, never a payload-supplied one.
        verify(boardAccessGuard).assertAccessible(boardId, userId);
    }

    // ---------------------------------------------------------------- board isolation

    @Test
    void messagesAreScopedToTheBoardTheyWereSentTo() {
        echoSave();
        UUID otherBoardId = UUID.randomUUID();

        chatService().post(boardId, principal(), "for this board only");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getBoardId()).isEqualTo(boardId).isNotEqualTo(otherBoardId);

        // Published only on this board's topic — never a shared or another board's one.
        verify(messagingTemplate).convertAndSend(
                eq("/topic/boards/" + boardId + "/chat"), any(ChatMessageResponse.class));
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/boards/" + otherBoardId + "/chat"), any(Object.class));
    }

    @Test
    void historyReadsOnlyTheRequestedBoard() {
        when(chatMessageRepository.findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), any()))
                .thenReturn(Page.empty());

        chatService().history(boardId, userId, PageRequest.of(0, 10));

        verify(chatMessageRepository).findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), any());
    }

    // ---------------------------------------------------------------- real-time delivery

    @Test
    void chatNeverTouchesTheCanvasOperationTopic() {
        echoSave();

        chatService().post(boardId, principal(), "hello");

        // The sequenced operation stream lives at the bare board topic; a chat message
        // arriving there would reach clients that parse everything on it as an Operation.
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/boards/" + boardId), any(Object.class));
    }

    @Test
    void successfulPersistencePublishesLocallyAndToOtherInstances() {
        echoSave();

        ChatMessageResponse response = chatService().post(boardId, principal(), "hello");

        verify(messagingTemplate).convertAndSend("/topic/boards/" + boardId + "/chat", response);
        verify(chatEventBroadcaster).broadcast(response);
    }

    @Test
    void persistenceFailurePublishesNothing() {
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenThrow(new RuntimeException("postgres is down"));
        ChatService service = chatService();
        UserPrincipal sender = principal();

        assertThatThrownBy(() -> service.post(boardId, sender, "hello"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("postgres is down");

        // Postgres is the source of truth: nothing unsaved may ever be broadcast.
        verifyNoInteractions(messagingTemplate, chatEventBroadcaster);
    }

    @Test
    void redisFailureDoesNotUndoAPersistedMessage() {
        echoSave();
        // The broadcaster swallows its own Redis errors; this asserts the service does
        // not treat a publish problem as a send failure for an already-durable message.
        doThrow(new RuntimeException("redis unavailable"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        ChatService service = chatService();
        UserPrincipal sender = principal();

        assertThatThrownBy(() -> service.post(boardId, sender, "hello"))
                .isInstanceOf(RuntimeException.class);

        // The row was written before anything was published, so it stays written —
        // there is no delete or compensating call.
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(chatMessageRepository, never()).delete(any());
        verify(chatMessageRepository, never()).deleteById(any());
    }

    // ---------------------------------------------------------------- history

    @Test
    void historyReturnsNewestPageOrderedOldestToNewestForDisplay() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        ChatMessage newest = message("third", base.plusSeconds(2));
        ChatMessage middle = message("second", base.plusSeconds(1));
        ChatMessage oldest = message("first", base);

        // The repository returns newest-first; the service flips it for reading.
        when(chatMessageRepository.findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), any()))
                .thenReturn(new PageImpl<>(List.of(newest, middle, oldest), PageRequest.of(0, 50), 3));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(User.builder().id(userId).name("Ishan").email("i@e.com").build()));

        Page<ChatMessageResponse> page = chatService().history(boardId, userId, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(ChatMessageResponse::message)
                .containsExactly("first", "second", "third");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).allSatisfy(m -> assertThat(m.userName()).isEqualTo("Ishan"));
    }

    @Test
    void historyPagesThroughWithTheRequestedPageNumber() {
        // 25 messages, page 2 of size 10 — the last page, holding the final 5. Sized to
        // match, because PageImpl recalculates the total when content and page
        // coordinates disagree.
        List<ChatMessage> lastPage = List.of(
                message("m21", Instant.now()), message("m22", Instant.now()),
                message("m23", Instant.now()), message("m24", Instant.now()),
                message("m25", Instant.now()));
        when(chatMessageRepository.findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), any()))
                .thenReturn(new PageImpl<>(lastPage, PageRequest.of(2, 10), 25));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        Page<ChatMessageResponse> page = chatService().history(boardId, userId, PageRequest.of(2, 10));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatMessageRepository).findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(25);
        // A sender who no longer resolves still renders rather than breaking the page.
        assertThat(page.getContent().get(0).userName()).isEqualTo("Unknown user");
    }

    @Test
    void oversizedPageRequestIsCappedRatherThanHonoured() {
        when(chatMessageRepository.findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), any()))
                .thenReturn(Page.empty());

        chatService().history(boardId, userId, PageRequest.of(0, 5_000));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatMessageRepository).findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(ChatService.MAX_PAGE_SIZE);
    }

    @Test
    void unpagedRequestFallsBackToTheDefaultPageSize() {
        when(chatMessageRepository.findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), any()))
                .thenReturn(Page.empty());

        chatService().history(boardId, userId, Pageable.unpaged());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatMessageRepository).findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(ChatService.DEFAULT_PAGE_SIZE);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void senderNamesAreResolvedInOneQueryForTheWholePage() {
        when(chatMessageRepository.findByBoardIdOrderByCreatedAtDescIdDesc(eq(boardId), any()))
                .thenReturn(new PageImpl<>(
                        List.of(message("a", Instant.now()), message("b", Instant.now()),
                                message("c", Instant.now())),
                        PageRequest.of(0, 50), 3));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(User.builder().id(userId).name("Ishan").email("i@e.com").build()));

        chatService().history(boardId, userId, PageRequest.of(0, 50));

        // One lookup for three messages, not one per row.
        verify(userRepository).findAllById(any());
    }

    private ChatMessage message(String text, Instant createdAt) {
        ChatMessage message = ChatMessage.of(boardId, userId, text);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }
}
