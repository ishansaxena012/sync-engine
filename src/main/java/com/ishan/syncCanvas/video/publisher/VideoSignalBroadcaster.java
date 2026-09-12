package com.ishan.syncCanvas.video.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.video.dto.VideoSignalMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Relays a signaling frame already handed to the local {@code SimpMessagingTemplate} out
 * to every other SyncEngine instance, in case the target participant's WebSocket
 * connection lives there instead. On its own Redis channel — signaling traffic (one
 * message per SDP offer/answer and per ICE candidate, potentially dozens per call) never
 * shares a channel with the low-volume room lifecycle events on
 * {@link com.ishan.syncCanvas.video.publisher.VideoEventBroadcaster}, nor with chat,
 * cursor, presence or canvas-operation pub/sub.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSignalBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic videoSignalingTopic;

    /** Random per-JVM identity, used by {@link VideoSignalSubscriber} to drop self-echoes. */
    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    public void broadcast(UUID boardId, UUID targetUserId, VideoSignalMessage message) {
        try {
            String json = objectMapper.writeValueAsString(new VideoSignalEnvelope(instanceId, boardId, targetUserId, message));
            redisTemplate.convertAndSend(videoSignalingTopic.getTopic(), json);
        } catch (Exception ex) {
            // Local delivery (attempted by the caller before this runs) already reached
            // the target if they happen to share this instance; a lost cross-instance
            // relay here must not fail the whole signaling send.
            log.error("Failed to relay video signal ({}) for board {} to Redis", message.type(), boardId, ex);
        }
    }
}
