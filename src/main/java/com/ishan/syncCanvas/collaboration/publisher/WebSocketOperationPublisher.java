package com.ishan.syncCanvas.collaboration.publisher;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketOperationPublisher implements OperationPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(UUID boardId, SequencedOperation sequencedOperation) {
        log.info("Publishing {} (seq {})", sequencedOperation.operation().type(), sequencedOperation.sequence());
        messagingTemplate.convertAndSend(
                "/topic/boards/" + boardId,
                sequencedOperation);
    }

    @Override
    public void publishError(UUID boardId, OperationErrorResponse error) {
        log.info("Publishing error to board {}: {}", boardId, error);
        messagingTemplate.convertAndSend(
                "/topic/boards/" + boardId + "/errors",
                error);
    }

}