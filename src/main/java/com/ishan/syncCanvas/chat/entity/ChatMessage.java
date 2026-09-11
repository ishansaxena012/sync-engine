package com.ishan.syncCanvas.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One durable chat message in a board's room.
 *
 * <p>Immutable once written and entirely server-assigned — a client supplies only the
 * text. Deliberately does not extend {@code BaseEntity}: there is no {@code updated_at}
 * because a message is never edited, the same reasoning as
 * {@link com.ishan.syncCanvas.collaboration.event.BoardEvent}.
 *
 * <p>Never returned over the wire directly; see
 * {@link com.ishan.syncCanvas.chat.dto.ChatMessageResponse}.
 */
@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor
public class ChatMessage {

    @Id
    private UUID id;

    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    /** Always the authenticated sender, resolved server-side — never taken from the payload. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = ChatMessage.MAX_LENGTH)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Matches the {@code VARCHAR(2000)} column width in V6__chat_messages.sql. */
    public static final int MAX_LENGTH = 2000;

    public static ChatMessage of(UUID boardId, UUID userId, String message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.id = UUID.randomUUID();
        chatMessage.boardId = boardId;
        chatMessage.userId = userId;
        chatMessage.message = message;
        chatMessage.createdAt = Instant.now();
        return chatMessage;
    }
}
