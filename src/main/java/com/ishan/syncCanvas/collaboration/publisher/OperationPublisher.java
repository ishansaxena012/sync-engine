package com.ishan.syncCanvas.collaboration.publisher;

import com.ishan.syncCanvas.collaboration.dto.OperationErrorResponse;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;

import java.util.UUID;

public interface OperationPublisher {

    void publish(UUID boardId, SequencedOperation sequencedOperation);

    void publishError(UUID boardId, OperationErrorResponse error);

}