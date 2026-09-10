package com.ishan.syncCanvas.collaboration.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Receives presence events broadcast by other SyncEngine instances and relays them to
 * this instance's own connected WebSocket clients — mirrors
 * {@link com.ishan.syncCanvas.collaboration.cursor.CursorEventSubscriber}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final PresenceEventBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            PresenceEventEnvelope envelope = objectMapper.readValue(json, PresenceEventEnvelope.class);

            if (broadcaster.getInstanceId().equals(envelope.originInstanceId())) {
                return; // our own broadcast; already delivered to local clients
            }

            messagingTemplate.convertAndSend(
                    "/topic/boards/" + envelope.boardId() + "/presence",
                    envelope.event());

        } catch (Exception ex) {
            log.error("Failed to relay presence event received from Redis", ex);
        }
    }
}
