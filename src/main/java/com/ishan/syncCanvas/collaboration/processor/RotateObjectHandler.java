package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.operation.RotateObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
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
    private final DirtySessionTracker dirtySessionTracker;

    @Override
    public Class<RotateObjectOperation> supports() {
        return RotateObjectOperation.class;
    }

    @Override
    public void handle(RotateObjectOperation operation) {

        log.info("Rotate handler started");

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

            object.setRotation(operation.rotation());

            session.incrementVersion();
            session.touch();
            dirtySessionTracker.markDirty(operation.boardId());

            log.info(
                    "Rotated object {} to {}°",
                    operation.objectId(),
                    operation.rotation());
            log.info(
                    "Dirty boards: {}",
                    dirtySessionTracker.getDirtyBoards());

        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}