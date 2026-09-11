package com.ishan.syncCanvas.chat.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.chat.dto.ChatMessageResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Relays a chat message already committed to Postgres and delivered locally out to
 * every other SyncEngine instance, so their own connected clients receive it too.
 * Same pattern as {@link com.ishan.syncCanvas.collaboration.presence.PresenceEventBroadcaster},
 * on a dedicated channel so chat traffic never touches cursor, presence or
 * canvas-operation pub/sub.
 *
 * <p>Failures are logged and swallowed on purpose. By the time this runs the message is
 * already durable, so propagating the failure would turn a delivery hiccup into a
 * user-visible send error for a message that was in fact saved; the affected clients
 * recover by re-reading history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic chatEventsTopic;

    /** Random per-JVM identity, used by {@link ChatEventSubscriber} to drop self-echoes. */
    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    public void broadcast(ChatMessageResponse message) {
        try {
            String json = objectMapper.writeValueAsString(new ChatEventEnvelope(instanceId, message));
            redisTemplate.convertAndSend(chatEventsTopic.getTopic(), json);
        } catch (Exception ex) {
            log.error("Failed to broadcast chat message {} for board {} to Redis — it is persisted "
                    + "but other instances will only see it on the next history fetch",
                    message.id(), message.boardId(), ex);
        }
    }
}
