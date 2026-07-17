package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
// import com.ishan.syncCanvas.canvas.model.CanvasObject;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MoveObjectHandler
        implements OperationHandler<MoveObjectOperation> {

    private final BoardSessionManager sessionManager;

    @Override
    public Class<MoveObjectOperation> supports() {
        return MoveObjectOperation.class;
    }

    @Override
    public void handle(MoveObjectOperation operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();

        try {

            CanvasObject object = session.getObject(operation.objectId());

            if (object == null) {
                throw new ObjectNotFoundException(operation.objectId());
            }

            object.setX(operation.x());
            object.setY(operation.y());

            session.incrementVersion();
            session.touch();

            log.debug(
                    "Moved object {} to ({}, {}) on board {}",
                    operation.objectId(),
                    operation.x(),
                    operation.y(),
                    operation.boardId());

        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}