package com.ishan.syncCanvas.collaboration.publisher;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.operation.Operation;

import java.util.UUID;

public interface OperationPublisher {

    void publish(UUID boardId, Operation operation);

    void publishError(OperationErrorResponse error);

}