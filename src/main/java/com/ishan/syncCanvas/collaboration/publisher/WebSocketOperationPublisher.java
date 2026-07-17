package com.ishan.syncCanvas.collaboration.publisher;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketOperationPublisher implements OperationPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(UUID boardId, Operation operation) {

        messagingTemplate.convertAndSend(
                "/topic/boards/" + boardId,
                operation);
    }

    @Override
    public void publishError(OperationErrorResponse error) {

        messagingTemplate.convertAndSendToUser(
                /* username */
                "test-user",
                "/queue/errors",
                error);

    }

}