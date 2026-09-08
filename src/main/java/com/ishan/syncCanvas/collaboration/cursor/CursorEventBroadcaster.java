package com.ishan.syncCanvas.collaboration.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes cursor events already applied on this instance to Redis, so every other
 * SyncEngine instance can relay them to its own connected WebSocket clients. Same
 * pattern as {@link com.ishan.syncCanvas.collaboration.publisher.RedisOperationBroadcaster},
 * on its own channel so cursor traffic never touches the canvas-operations pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CursorEventBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic cursorEventsTopic;

    /** Random per-JVM identity, used by {@link CursorEventSubscriber} to drop self-echoes. */
    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    public void broadcast(UUID boardId, CursorEvent event) {
        try {
            String json = objectMapper.writeValueAsString(new CursorEventEnvelope(instanceId, boardId, event));
            redisTemplate.convertAndSend(cursorEventsTopic.getTopic(), json);
        } catch (Exception ex) {
            log.error("Failed to broadcast cursor event for board {} to Redis", boardId, ex);
        }
    }
}
