package com.ishan.syncCanvas.collaboration.publisher;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import com.ishan.syncCanvas.collaboration.processor.ApplyMode;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A cross-instance relay must apply as REPLAY, not LIVE: this operation was already
 * accepted and durably committed on the originating instance, so re-checking
 * expectedVersion here would spuriously reject it whenever local delivery ordering makes
 * this instance's cache momentarily lag — exactly the bug this regression-tests.
 */
@ExtendWith(MockitoExtension.class)
class RedisOperationSubscriberTest {

    @Mock
    private BoardSessionManager sessionManager;
    @Mock
    private OperationProcessor operationProcessor;
    @Mock
    private WebSocketOperationPublisher webSocketOperationPublisher;
    @Mock
    private RedisOperationBroadcaster broadcaster;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private RedisOperationSubscriber subscriber;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        subscriber = new RedisOperationSubscriber(
                objectMapper, sessionManager, operationProcessor, webSocketOperationPublisher, broadcaster);
        when(broadcaster.getInstanceId()).thenReturn("this-instance");
    }

    private Message redisMessage(String originInstanceId, long sequence, MoveObjectOperation op) throws Exception {
        String json = objectMapper.writeValueAsString(new RedisOperationMessage(originInstanceId, sequence, op));
        Message message = org.mockito.Mockito.mock(Message.class);
        when(message.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    @Test
    void appliesARelayedOperationInReplayModeNotLive() throws Exception {
        MoveObjectOperation op = new MoveObjectOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(), UUID.randomUUID(), 99L, 1, 1);
        BoardSession session = new BoardSession(boardId);
        when(sessionManager.getSession(boardId)).thenReturn(java.util.Optional.of(session));

        subscriber.onMessage(redisMessage("other-instance", 5L, op), null);

        verify(operationProcessor).apply(eq(op), eq(session), eq(ApplyMode.REPLAY));
        verify(operationProcessor, never()).process(any());
    }

    @Test
    void relaysToLocalWebSocketClientsWithTheOriginatingSequenceEvenWithNoLocalSession() throws Exception {
        MoveObjectOperation op = new MoveObjectOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(), UUID.randomUUID(), 1L, 1, 1);
        when(sessionManager.getSession(boardId)).thenReturn(java.util.Optional.empty());

        subscriber.onMessage(redisMessage("other-instance", 5L, op), null);

        verify(operationProcessor, never()).apply(any(), any(), any());
        verify(webSocketOperationPublisher).publish(eq(boardId), eq(new SequencedOperation(5L, op)));
    }

    @Test
    void ownEchoIsIgnored() throws Exception {
        MoveObjectOperation op = new MoveObjectOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(), UUID.randomUUID(), 1L, 1, 1);

        subscriber.onMessage(redisMessage("this-instance", 5L, op), null);

        verify(operationProcessor, never()).apply(any(), any(), any());
        verify(webSocketOperationPublisher, never()).publish(any(), any());
    }
}
