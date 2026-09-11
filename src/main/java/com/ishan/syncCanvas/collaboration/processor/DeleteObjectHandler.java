package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.DeleteObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import com.ishan.syncCanvas.collaboration.undo.CanvasObjectSnapshot;
import com.ishan.syncCanvas.collaboration.undo.UndoableChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteObjectHandler
        implements OperationHandler<DeleteObjectOperation> {

    private final BoardSessionManager sessionManager;
    private final DirtySessionTracker dirtySessionTracker;

    @Override
    public Class<DeleteObjectOperation> supports() {
        return DeleteObjectOperation.class;
    }

    @Override
    public UndoableChange handle(DeleteObjectOperation operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();
        try {
            UndoableChange change = apply(operation, session, ApplyMode.LIVE);
            dirtySessionTracker.markDirty(operation.boardId());
            return change;
        } finally {
            session.getLock().writeLock().unlock();
        }
    }

    @Override
    public UndoableChange apply(DeleteObjectOperation operation, BoardSession session, ApplyMode mode) {
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

        CanvasObjectSnapshot beforeDelete = CanvasObjectSnapshot.of(object);

        if (!session.removeObject(operation.objectId())) {
            throw new ObjectNotFoundException(operation.objectId());
        }

        log.debug("Deleted object {} from board {} ({})", operation.objectId(), operation.boardId(), mode);

        return new UndoableChange.DeleteChange(beforeDelete);
    }
}
