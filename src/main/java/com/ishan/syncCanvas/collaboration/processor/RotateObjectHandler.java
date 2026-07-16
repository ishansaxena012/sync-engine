package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.RotateObjectOperation;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RotateObjectHandler
        implements OperationHandler<RotateObjectOperation> {

    private final BoardSessionManager sessionManager;

    @Override
    public Class<RotateObjectOperation> supports() {
        return RotateObjectOperation.class;
    }

    @Override
    public void handle(RotateObjectOperation operation) {

        // TODO:
        // Retrieve BoardSession
        // Acquire write lock
        // Rotate object
        // Increment session version
        // Publish event

        log.debug("Processing ROTATE_OBJECT for board {}", operation.boardId());
    }
}