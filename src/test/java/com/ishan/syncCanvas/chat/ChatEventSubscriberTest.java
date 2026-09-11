package com.ishan.syncCanvas.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ishan.syncCanvas.chat.dto.ChatMessageResponse;
import com.ishan.syncCanvas.chat.publisher.ChatEventBroadcaster;
import com.ishan.syncCanvas.chat.publisher.ChatEventEnvelope;
import com.ishan.syncCanvas.chat.publisher.ChatEventSubscriber;
import com.ishan.syncCanvas.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatEventSubscriberTest {

    @Mock
    private ChatEventBroadcaster broadcaster;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private Message redisMessage;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ObjectMapper objectMapper;
    private ChatEventSubscriber subscriber;

    private final UUID boardId = UUID.randomUUID();
    private final String ownInstanceId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber = new ChatEventSubscriber(objectMapper, broadcaster, messagingTemplate);
        lenient().when(broadcaster.getInstanceId()).thenReturn(ownInstanceId);
    }

    private ChatMessageResponse message() {
        return new ChatMessageResponse(UUID.randomUUID(), boardId, UUID.randomUUID(),
                "Ishan", "Hello everyone!", Instant.parse("2026-01-01T10:00:00Z"));
    }

    private void deliver(ChatEventEnvelope envelope) throws Exception {
        when(redisMessage.getBody())
                .thenReturn(objectMapper.writeValueAsBytes(envelope));
        subscriber.onMessage(redisMessage, null);
    }

    @Test
    void messageFromAnotherInstanceIsRelayedToLocalClients() throws Exception {
        ChatMessageResponse message = message();

        deliver(new ChatEventEnvelope(UUID.randomUUID().toString(), message));

        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/boards/" + boardId + "/chat"),
                org.mockito.ArgumentMatchers.<Object>argThat(relayed ->
                        relayed instanceof ChatMessageResponse response
                                && response.id().equals(message.id())
                                && response.message().equals("Hello everyone!")
                                && response.userName().equals("Ishan")));
    }

    @Test
    void ourOwnBroadcastIsDroppedSoLocalClientsDoNotSeeItTwice() throws Exception {
        deliver(new ChatEventEnvelope(ownInstanceId, message()));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void relayNeverWritesToPostgres() throws Exception {
        deliver(new ChatEventEnvelope(UUID.randomUUID().toString(), message()));

        // The originating instance already committed the row. Persisting again on every
        // receiving instance is exactly how one message becomes N duplicates.
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void malformedPayloadIsLoggedAndDroppedWithoutBreakingTheListener() {
        when(redisMessage.getBody()).thenReturn("{not json".getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(redisMessage, null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
