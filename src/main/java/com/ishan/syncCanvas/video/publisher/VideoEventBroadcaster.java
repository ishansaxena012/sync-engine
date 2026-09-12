package com.ishan.syncCanvas.video.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.video.dto.VideoRoomEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Relays a video-room lifecycle/membership event already applied and delivered locally
 * out to every other SyncEngine instance, so their own connected clients receive it
 * too. Same pattern as {@link com.ishan.syncCanvas.collaboration.presence.PresenceEventBroadcaster},
 * on its own channel — video traffic never shares a channel with chat, cursor,
 * presence or canvas-operation pub/sub. Carries only room lifecycle/membership events;
 * no media, SDP or ICE data ever crosses this channel (that belongs to a later phase).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoEventBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic videoEventsTopic;

    /** Random per-JVM identity, used by {@link VideoEventSubscriber} to drop self-echoes. */
    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    public void broadcast(VideoRoomEvent event) {
        try {
            String json = objectMapper.writeValueAsString(new VideoEventEnvelope(instanceId, event));
            redisTemplate.convertAndSend(videoEventsTopic.getTopic(), json);
        } catch (Exception ex) {
            log.error("Failed to broadcast video room event {} for board {} to Redis",
                    event.type(), event.boardId(), ex);
        }
    }
}
