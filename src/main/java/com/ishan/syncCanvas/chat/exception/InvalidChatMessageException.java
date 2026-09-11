package com.ishan.syncCanvas.chat.exception;

/**
 * A chat message the server refuses to store: blank, or longer than
 * {@link com.ishan.syncCanvas.chat.entity.ChatMessage#MAX_LENGTH}.
 *
 * <p>Rejected outright rather than truncated — silently shortening someone's message
 * and then broadcasting it as theirs is worse than telling them it was too long.
 */
public class InvalidChatMessageException extends RuntimeException {

    public InvalidChatMessageException(String message) {
        super(message);
    }
}
