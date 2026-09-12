package com.ishan.syncCanvas.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ishan.syncCanvas.video.dto.VideoRoomBroadcastEvent;
import com.ishan.syncCanvas.video.dto.VideoRoomEvent;
import com.ishan.syncCanvas.video.dto.VideoRoomEventType;
import com.ishan.syncCanvas.video.publisher.VideoEventBroadcaster;
import com.ishan.syncCanvas.video.publisher.VideoEventEnvelope;
import com.ishan.syncCanvas.video.publisher.VideoEventSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoEventSubscriberTest {

    @Mock
    private VideoEventBroadcaster broadcaster;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private Message redisMessage;

    private ObjectMapper objectMapper;
    private VideoEventSubscriber subscriber;

    private final UUID boardId = UUID.randomUUID();
    private final String ownInstanceId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber = new VideoEventSubscriber(objectMapper, broadcaster, messagingTemplate);
        lenient().when(broadcaster.getInstanceId()).thenReturn(ownInstanceId);
    }

    private VideoRoomEvent roomStartedEvent() {
        return new VideoRoomEvent(VideoRoomEventType.ROOM_STARTED, boardId, null, List.of(), System.currentTimeMillis());
    }

    private void deliver(VideoEventEnvelope envelope) throws Exception {
        when(redisMessage.getBody()).thenReturn(objectMapper.writeValueAsBytes(envelope));
        subscriber.onMessage(redisMessage, null);
    }

    @Test
    void eventFromAnotherInstanceIsRelayedToLocalClients() throws Exception {
        VideoRoomEvent event = roomStartedEvent();

        deliver(new VideoEventEnvelope(UUID.randomUUID().toString(), event));

        verify(messagingTemplate).convertAndSend(
                "/topic/boards/" + boardId + "/video",
                VideoRoomBroadcastEvent.from(event));
    }

    @Test
    void ourOwnBroadcastIsDroppedSoLocalClientsDoNotSeeItTwice() throws Exception {
        deliver(new VideoEventEnvelope(ownInstanceId, roomStartedEvent()));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void malformedPayloadIsLoggedAndDroppedWithoutBreakingTheListener() {
        when(redisMessage.getBody()).thenReturn("{not json".getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(redisMessage, null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
