package com.ishan.syncCanvas.collaboration.publisher;

import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.operation.Operation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes operations already accepted on this instance to Redis, so every other
 * SyncEngine instance can apply them to its own local board sessions and re-broadcast
 * them to its own WebSocket clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOperationBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic boardOperationsTopic;

    /** Random per-JVM identity, used by {@link RedisOperationSubscriber} to drop self-echoes. */
    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    public void broadcast(long sequence, Operation operation) {
        try {
            String json = objectMapper.writeValueAsString(
                    new RedisOperationMessage(instanceId, sequence, operation));
            redisTemplate.convertAndSend(boardOperationsTopic.getTopic(), json);
        } catch (Exception ex) {
            log.error("Failed to broadcast operation {} to Redis", operation.operationId(), ex);
        }
    }
}
