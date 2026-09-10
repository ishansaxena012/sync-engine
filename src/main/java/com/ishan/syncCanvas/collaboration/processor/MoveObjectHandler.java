package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

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

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();
        try {
            apply(operation, session, ApplyMode.LIVE);
            dirtySessionTracker.markDirty(operation.boardId());
        } finally {
            session.getLock().writeLock().unlock();
        }
    }

    @Override
    public void apply(MoveObjectOperation operation, BoardSession session, ApplyMode mode) {
        if (operation.objectId() == null) {
            throw new IllegalArgumentException("Object ID cannot be null");
        }

        CanvasObject object = session.getObject(operation.objectId());
        if (object == null) {
            throw new ObjectNotFoundException(operation.objectId());
        }

        if (mode == ApplyMode.LIVE
                && operation.expectedVersion() != null
                && !Objects.equals(object.getVersion(), operation.expectedVersion())) {
            throw new VersionMismatchException(operation.expectedVersion(), object.getVersion());
        }

        object.setX(operation.x());
        object.setY(operation.y());
        object.setVersion(object.getVersion() != null ? object.getVersion() + 1 : 1L);

        session.incrementVersion();
        session.touch();

        log.debug("Moved object {} to ({}, {}) on board {} ({})",
                operation.objectId(), operation.x(), operation.y(), operation.boardId(), mode);
    }
}
