package com.ishan.syncCanvas.chat.dto;

import com.ishan.syncCanvas.chat.entity.ChatMessage;

import java.time.Instant;
import java.util.UUID;

/**
 * The canonical, server-authored form of a chat message — what goes out on
 * {@code /topic/boards/{boardId}/chat}, what the history endpoint returns, and what
 * crosses the Redis channel between instances. The sender receives exactly this too,
 * rather than a private echo of their own request, so every participant renders the
 * same object.
 *
 * <p>{@code userName} is a display convenience resolved at read time; it is not stored
 * on the message, so a user who later changes their name is shown consistently
 * everywhere rather than frozen at the name they had when they typed.
 */
public record ChatMessageResponse(
        UUID id,
        UUID boardId,
        UUID userId,
        String userName,
        String message,
        Instant createdAt) {

    public static ChatMessageResponse from(ChatMessage message, String userName) {
        return new ChatMessageResponse(
                message.getId(),
                message.getBoardId(),
                message.getUserId(),
                userName,
                message.getMessage(),
                message.getCreatedAt());
    }
}
