package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.CreateObjectOperation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateObjectHandler
        implements OperationHandler<CreateObjectOperation> {

    private final BoardSessionManager sessionManager;

    @Override
    public Class<CreateObjectOperation> supports() {
        return CreateObjectOperation.class;
    }

    @Override
    public void handle(CreateObjectOperation operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();

        try {

            session.addObject(operation.canvasObject());

            log.debug(
                    "Canvas object {} created on board {}",
                    operation.canvasObject().getId(),
                    operation.boardId());

        } finally {

            session.getLock().writeLock().unlock();

        }
    }
}