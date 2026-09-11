package com.ishan.syncCanvas.collaboration.lifecycle;

import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Relays a board-closed notification to every other SyncEngine instance, so their
 * locally-connected WebSocket clients hear about the deletion too — the local broker on
 * the instance that handled the HTTP delete only reaches clients connected to it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardClosureBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic boardClosureTopic;

    /** Random per-JVM identity, used by {@link BoardClosureSubscriber} to drop self-echoes. */
    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    public void broadcast(BoardClosedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(new BoardClosureEnvelope(instanceId, event));
            redisTemplate.convertAndSend(boardClosureTopic.getTopic(), json);
        } catch (Exception ex) {
            log.error("Failed to broadcast board-closed event for board {} to Redis", event.boardId(), ex);
        }
    }
}
