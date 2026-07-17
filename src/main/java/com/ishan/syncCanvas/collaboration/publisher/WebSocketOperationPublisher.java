package com.ishan.syncCanvas.collaboration.publisher;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.operation.Operation;
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
    public void publish(UUID boardId, Operation operation) {
        log.info("Publishing {}", operation.type());
        messagingTemplate.convertAndSend(
                "/topic/boards/" + boardId,
                operation);
    }

    @Override
    public void publishError(OperationErrorResponse error) {
        log.info("error: WebSocketOperationPublisher   || ", error);
        messagingTemplate.convertAndSendToUser(
                /* username */
                "test-user",
                "/queue/errors",
                error);

    }

}