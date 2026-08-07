package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.ChangePayloadOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
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
    private final DirtySessionTracker dirtySessionTracker;

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
            log.info("Objects in session: {}", session.getObjects());
            log.info("Looking for: {}", operation.objectId());

            CanvasObject object = session.getObject(operation.objectId());

            if (object == null) {
                throw new ObjectNotFoundException(operation.objectId());
            }
            
            if (operation.expectedVersion() != null && !java.util.Objects.equals(object.getVersion(), operation.expectedVersion())) {
                throw new VersionMismatchException(
                        operation.expectedVersion(),
                        object.getVersion());
            }

            object.changePayload(operation.payload());

            if (object.getVersion() != null) {
                object.setVersion(object.getVersion() + 1);
            } else {
                object.setVersion(1L);
            }

            session.incrementVersion();
            session.touch();
            dirtySessionTracker.markDirty(operation.boardId());
            log.debug(
                    "Updated payload of object {} on board {}",
                    operation.objectId(),
                    operation.boardId());
        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}