package com.ishan.syncCanvas.collaboration.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes presence events already applied on this instance to Redis, so every other
 * SyncEngine instance can relay them to its own connected WebSocket clients. Same
 * pattern as {@link com.ishan.syncCanvas.collaboration.cursor.CursorEventBroadcaster},
 * on its own channel so presence traffic never touches cursor or canvas-operation pub/sub.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic presenceEventsTopic;

    /** Random per-JVM identity, used by {@link PresenceEventSubscriber} to drop self-echoes. */
    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    public void broadcast(UUID boardId, PresenceEvent event) {
        try {
            String json = objectMapper.writeValueAsString(new PresenceEventEnvelope(instanceId, boardId, event));
            redisTemplate.convertAndSend(presenceEventsTopic.getTopic(), json);
        } catch (Exception ex) {
            log.error("Failed to broadcast presence event for board {} to Redis", boardId, ex);
        }
    }
}
