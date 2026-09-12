package com.ishan.syncCanvas.video.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Receives signaling frames broadcast by other SyncEngine instances and attempts local
 * delivery to the target user. {@code convertAndSendToUser} only resolves to a session
 * connected to <em>this</em> instance, so exactly one instance — whichever actually
 * holds the target's WebSocket connection — succeeds; every other instance's attempt is
 * a harmless no-op. This is what makes cross-instance targeted delivery correct without
 * any instance needing to know where the target is actually connected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSignalSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final VideoSignalBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            VideoSignalEnvelope envelope = objectMapper.readValue(json, VideoSignalEnvelope.class);

            if (broadcaster.getInstanceId().equals(envelope.originInstanceId())) {
                return; // this instance already attempted local delivery before publishing
            }

            messagingTemplate.convertAndSendToUser(
                    envelope.targetUserId().toString(),
                    "/queue/boards/" + envelope.boardId() + "/video/signal",
                    envelope.message());

        } catch (Exception ex) {
            log.error("Failed to relay video signal received from Redis", ex);
        }
    }
}
