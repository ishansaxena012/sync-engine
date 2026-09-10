package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.RotateObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

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
    public void apply(RotateObjectOperation operation, BoardSession session, ApplyMode mode) {
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

        object.setRotation(operation.rotation());
        object.setVersion(object.getVersion() != null ? object.getVersion() + 1 : 1L);

        session.incrementVersion();
        session.touch();

        log.debug("Rotated object {} to {} on board {} ({})",
                operation.objectId(), operation.rotation(), operation.boardId(), mode);
    }
}
