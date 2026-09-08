package com.ishan.syncCanvas.collaboration.publisher;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Receives operations broadcast by other SyncEngine instances via Redis.
 *
 * <p>If this instance already has an active {@link com.ishan.syncCanvas.collaboration.session.BoardSession}
 * for the board (i.e. it has local clients interested in it), the operation is applied
 * to that local in-memory copy via the same {@link OperationProcessor} used for
 * locally-submitted operations, so this instance's cache and future persistence stay
 * consistent. In all cases the operation is re-broadcast to this instance's own
 * WebSocket clients so cross-instance viewers see the update.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOperationSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final BoardSessionManager sessionManager;
    private final OperationProcessor operationProcessor;
    private final WebSocketOperationPublisher webSocketOperationPublisher;
    private final RedisOperationBroadcaster broadcaster;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            RedisOperationMessage envelope = objectMapper.readValue(json, RedisOperationMessage.class);

            if (broadcaster.getInstanceId().equals(envelope.originInstanceId())) {
                // Our own broadcast echoed back; already processed and broadcast locally.
                return;
            }

            Operation operation = envelope.operation();

            if (sessionManager.exists(operation.boardId())) {
                try {
                    operationProcessor.process(operation);
                } catch (Exception ex) {
                    // The operation was already accepted and applied on the originating
                    // instance — this instance's local session state may now be behind,
                    // but its own WebSocket clients must still see the update, so the
                    // re-broadcast below must not be skipped just because local
                    // application failed.
                    log.error("Failed to apply operation {} from Redis to local session for board {}",
                            operation.operationId(), operation.boardId(), ex);
                }
            }

            webSocketOperationPublisher.publish(operation.boardId(), operation);

        } catch (Exception ex) {
            log.error("Failed to apply operation received from Redis", ex);
        }
    }
}
