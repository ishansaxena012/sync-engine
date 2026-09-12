package com.ishan.syncCanvas.video.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.video.dto.VideoRoomBroadcastEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Receives video-room events broadcast by other SyncEngine instances and relays them to
 * this instance's own connected WebSocket clients — mirrors
 * {@link com.ishan.syncCanvas.collaboration.presence.PresenceEventSubscriber}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final VideoEventBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            VideoEventEnvelope envelope = objectMapper.readValue(json, VideoEventEnvelope.class);

            if (broadcaster.getInstanceId().equals(envelope.originInstanceId())) {
                return; // our own broadcast; already delivered to local clients
            }

            messagingTemplate.convertAndSend(
                    "/topic/boards/" + envelope.event().boardId() + "/video",
                    VideoRoomBroadcastEvent.from(envelope.event()));

        } catch (Exception ex) {
            log.error("Failed to relay video room event received from Redis", ex);
        }
    }
}
