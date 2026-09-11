package com.ishan.syncCanvas.chat.service;

import com.ishan.syncCanvas.chat.dto.ChatMessageResponse;
import com.ishan.syncCanvas.chat.entity.ChatMessage;
import com.ishan.syncCanvas.chat.exception.InvalidChatMessageException;
import com.ishan.syncCanvas.chat.publisher.ChatEventBroadcaster;
import com.ishan.syncCanvas.chat.repository.ChatMessageRepository;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Board room chat: validation, durable persistence, and real-time fan-out.
 *
 * <p>Chat is a standalone collaboration channel. It shares this project's
 * authentication, {@link BoardAccessGuard} authorization and Redis relay mechanism, but
 * it has no board sequence, produces no {@code BoardEvent}, is not snapshotted or
 * reconstructed, and takes no part in undo/redo. Losing every chat message would leave
 * the canvas byte-identical.
 *
 * <p>Durability ordering is the point of the method ordering in {@link #post}: Postgres
 * commits first and only then is anything published. A persistence failure therefore
 * publishes nothing, and a publish failure never rolls back a committed message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** Upper bound on a caller-supplied page size, so no request can ask for the whole history. */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 50;

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final BoardAccessGuard boardAccessGuard;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatEventBroadcaster chatEventBroadcaster;

    /**
     * Validates, persists and publishes one message from {@code sender}.
     *
     * <p>Deliberately not {@code @Transactional}. {@code save} runs in its own
     * repository-level transaction that has committed by the time it returns, which is
     * precisely the guarantee the publish steps below depend on — inside an outer
     * transaction they would instead run pre-commit, and a later rollback would leave
     * every participant showing a message that does not exist.
     *
     * @return the canonical message, identical to what every other participant receives
     */
    public ChatMessageResponse post(UUID boardId, UserPrincipal sender, String rawMessage) {
        // Throws BoardAccessDeniedException, which the caller surfaces — never silently
        // dropped, so a user whose access was revoked mid-session finds out.
        boardAccessGuard.assertAccessible(boardId, sender.getId());

        String message = validate(boardId, sender.getId(), rawMessage);

        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.of(boardId, sender.getId(), message));

        // Length, not content: chat text is user data and has no place in server logs.
        log.info("Chat message {} persisted for board {} by user {} ({} chars)",
                saved.getId(), boardId, sender.getId(), message.length());

        ChatMessageResponse response = ChatMessageResponse.from(saved, sender.getDisplayName());

        // Clients attached to this instance, then everyone else's. The sender is
        // subscribed to the same topic and receives this like anyone else, so there is
        // no separate sender-only reply to keep consistent.
        messagingTemplate.convertAndSend("/topic/boards/" + boardId + "/chat", response);
        chatEventBroadcaster.broadcast(response);

        return response;
    }

    /**
     * One page of a board's history, oldest → newest within the page, with page 0 being
     * the most recent messages — which is what a chat panel opens on.
     */
    public Page<ChatMessageResponse> history(UUID boardId, UUID userId, Pageable pageable) {
        boardAccessGuard.assertAccessible(boardId, userId);

        Page<ChatMessage> page = chatMessageRepository
                .findByBoardIdOrderByCreatedAtDescIdDesc(boardId, clamp(pageable));

        Map<UUID, String> names = resolveNames(page.getContent());

        // Reverse in place of the query's DESC: newest-first is how you *select* the
        // most recent page, oldest-first is how you *read* it.
        List<ChatMessageResponse> ordered = new ArrayList<>(page.getContent().size());
        for (ChatMessage message : page.getContent()) {
            ordered.add(ChatMessageResponse.from(
                    message, names.getOrDefault(message.getUserId(), "Unknown user")));
        }
        Collections.reverse(ordered);

        return new PageImpl<>(ordered, page.getPageable(), page.getTotalElements());
    }

    private String validate(UUID boardId, UUID userId, String rawMessage) {
        // Surrounding whitespace is noise; interior text, including any Unicode, is
        // left exactly as sent.
        String message = rawMessage == null ? "" : rawMessage.strip();

        if (message.isEmpty()) {
            log.warn("Rejected blank chat message from user {} on board {}", userId, boardId);
            throw new InvalidChatMessageException("Message must not be blank");
        }
        if (message.length() > ChatMessage.MAX_LENGTH) {
            log.warn("Rejected oversized chat message ({} chars) from user {} on board {}",
                    message.length(), userId, boardId);
            throw new InvalidChatMessageException(
                    "Message must be at most " + ChatMessage.MAX_LENGTH + " characters");
        }
        return message;
    }

    /** One query for the whole page's senders rather than one per message. */
    private Map<UUID, String> resolveNames(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        Set<UUID> userIds = messages.stream()
                .map(ChatMessage::getUserId)
                .collect(Collectors.toSet());

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (first, second) -> first));
    }

    /**
     * Caps the requested page size and pins the sort, so neither can be widened from the
     * query string into an unbounded or unindexed scan.
     */
    private Pageable clamp(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), size);
    }
}
