package com.ishan.syncCanvas.collaboration.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Receives cursor events broadcast by other SyncEngine instances and relays them to
 * this instance's own connected WebSocket clients — the cross-instance half of cursor
 * sync, mirroring {@link com.ishan.syncCanvas.collaboration.publisher.RedisOperationSubscriber}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CursorEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final CursorEventBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            CursorEventEnvelope envelope = objectMapper.readValue(json, CursorEventEnvelope.class);

            if (broadcaster.getInstanceId().equals(envelope.originInstanceId())) {
                return; // our own broadcast; already delivered to local clients
            }

            messagingTemplate.convertAndSend(
                    "/topic/boards/" + envelope.boardId() + "/cursor",
                    envelope.event());

        } catch (Exception ex) {
            log.error("Failed to relay cursor event received from Redis", ex);
        }
    }
}
