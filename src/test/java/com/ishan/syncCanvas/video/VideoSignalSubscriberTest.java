package com.ishan.syncCanvas.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.video.dto.VideoSignalMessage;
import com.ishan.syncCanvas.video.dto.VideoSignalType;
import com.ishan.syncCanvas.video.publisher.VideoSignalBroadcaster;
import com.ishan.syncCanvas.video.publisher.VideoSignalEnvelope;
import com.ishan.syncCanvas.video.publisher.VideoSignalSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoSignalSubscriberTest {

    @Mock
    private VideoSignalBroadcaster broadcaster;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private Message redisMessage;

    private ObjectMapper objectMapper;
    private VideoSignalSubscriber subscriber;

    private final UUID boardId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final String ownInstanceId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        subscriber = new VideoSignalSubscriber(objectMapper, broadcaster, messagingTemplate);
        lenient().when(broadcaster.getInstanceId()).thenReturn(ownInstanceId);
    }

    private VideoSignalMessage offerMessage() {
        return new VideoSignalMessage(
                VideoSignalType.OFFER, boardId, UUID.randomUUID(), "Ishan",
                objectMapper.createObjectNode().put("sdp", "v=0..."), System.currentTimeMillis());
    }

    private void deliver(VideoSignalEnvelope envelope) throws Exception {
        when(redisMessage.getBody()).thenReturn(objectMapper.writeValueAsBytes(envelope));
        subscriber.onMessage(redisMessage, null);
    }

    @Test
    void signalFromAnotherInstanceIsDeliveredToTheLocalTargetOnly() throws Exception {
        VideoSignalMessage message = offerMessage();

        deliver(new VideoSignalEnvelope(UUID.randomUUID().toString(), targetUserId, message));

        verify(messagingTemplate).convertAndSendToUser(
                targetUserId.toString(), "/queue/boards/" + boardId + "/video/signal", message);
    }

    @Test
    void ourOwnRelayIsDroppedSoTheTargetDoesNotReceiveItTwice() throws Exception {
        deliver(new VideoSignalEnvelope(ownInstanceId, targetUserId, offerMessage()));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void malformedEnvelopeIsLoggedAndDroppedWithoutBreakingTheListener() {
        when(redisMessage.getBody()).thenReturn("{not json".getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(redisMessage, null);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }
}
