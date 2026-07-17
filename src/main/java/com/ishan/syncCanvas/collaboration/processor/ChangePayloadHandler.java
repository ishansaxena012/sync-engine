package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.operation.ChangePayloadOperation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChangePayloadHandler
        implements OperationHandler<ChangePayloadOperation> {

    private final BoardSessionManager sessionManager;

    @Override
    public Class<ChangePayloadOperation> supports() {
        return ChangePayloadOperation.class;
    }

    @Override
    public void handle(ChangePayloadOperation operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();

        try {

            if (operation.objectId() == null) {
                throw new IllegalArgumentException("Object ID cannot be null");
            }

            com.ishan.syncCanvas.canvas.entity.CanvasObject object = session.getObject(operation.objectId());

            if (object == null) {
                throw new ObjectNotFoundException(operation.objectId());
            }

            object.changePayload(operation.payload());

            session.incrementVersion();
            session.touch();

            log.debug(
                    "Updated payload of object {} on board {}",
                    operation.objectId(),
                    operation.boardId());

        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}