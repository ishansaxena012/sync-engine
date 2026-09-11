package com.ishan.syncCanvas.chat.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Receives chat messages broadcast by other SyncEngine instances and relays them to
 * this instance's own connected WebSocket clients — mirrors
 * {@link com.ishan.syncCanvas.collaboration.presence.PresenceEventSubscriber}.
 *
 * <p>Nothing is persisted here. The originating instance already committed the message;
 * writing it again on every receiving instance is exactly how a single message would
 * become N duplicate rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ChatEventBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatEventEnvelope envelope = objectMapper.readValue(json, ChatEventEnvelope.class);

            if (broadcaster.getInstanceId().equals(envelope.originInstanceId())) {
                return; // our own broadcast; already delivered to local clients
            }

            messagingTemplate.convertAndSend(
                    "/topic/boards/" + envelope.message().boardId() + "/chat",
                    envelope.message());

        } catch (Exception ex) {
            log.error("Failed to relay chat message received from Redis", ex);
        }
    }
}
