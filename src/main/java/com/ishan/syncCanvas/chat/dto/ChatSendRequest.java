package com.ishan.syncCanvas.chat.dto;

/**
 * The entire client-supplied half of a chat message.
 *
 * <p>Sender identity and timestamp are intentionally absent: both are taken from the
 * authenticated STOMP session and the server clock, so a client cannot post as someone
 * else or backdate a message. Board id comes from the destination, not the body.
 */
public record ChatSendRequest(String message) {
}
