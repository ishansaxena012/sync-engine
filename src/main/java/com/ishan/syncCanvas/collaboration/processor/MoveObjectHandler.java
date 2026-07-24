package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
// import com.ishan.syncCanvas.canvas.model.CanvasObject;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
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
    private final DirtySessionTracker dirtySessionTracker;

    @Override
    public Class<MoveObjectOperation> supports() {
        return MoveObjectOperation.class;
    }

    @Override
    public void handle(MoveObjectOperation operation) {

        log.info("Move handler started");
        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();
        log.info("Move handler lock acquired");
        try {

            log.info("Move handler inside try block");

            if (operation.objectId() == null) {
                throw new IllegalArgumentException("Object ID cannot be null");
            }

            CanvasObject object = session.getObject(operation.objectId());

            if (object == null) {
                throw new ObjectNotFoundException(operation.objectId());
            }

            object.setX(operation.x());
            object.setY(operation.y());

            session.incrementVersion();
            session.touch();
            log.info(
                    "Memory object {} version = {}",
                    object.getId(),
                    object.getVersion());

            dirtySessionTracker.markDirty(
                    operation.boardId());

            log.info(
                    "Dirty boards: {}",
                    dirtySessionTracker.getDirtyBoards());

            log.info(
                    "Object moved to ({}, {})",
                    operation.x(),
                    operation.y());

            log.debug(
                    "Moved object {} to ({}, {}) on board {}",
                    operation.objectId(),
                    operation.x(),
                    operation.y(),
                    operation.boardId());

        } finally {
            session.getLock().writeLock().unlock();
        }
        log.info(
                "Moved object {} -> ({}, {})",
                operation.objectId(),
                operation.x(),
                operation.y());
    }
}