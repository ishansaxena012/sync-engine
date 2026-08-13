package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.BulkMoveObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class BulkMoveObjectHandler implements OperationHandler<BulkMoveObjectOperation> {

    private final BoardSessionManager sessionManager;
    private final DirtySessionTracker dirtySessionTracker;

    @Override
    public Class<BulkMoveObjectOperation> supports() {
        return BulkMoveObjectOperation.class;
    }

    @Override
    public void handle(BulkMoveObjectOperation operation) {
        log.info("Bulk move handler started");
        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: " + operation.boardId()));

        session.getLock().writeLock().lock();
        try {
            if (operation.moves() == null || operation.moves().isEmpty()) {
                throw new IllegalArgumentException("Moves cannot be null or empty");
            }

            // First pass: resolve and validate every move without mutating anything, so a
            // failure partway through never leaves earlier objects in this batch half-applied.
            List<CanvasObject> resolved = new ArrayList<>(operation.moves().size());

            for (BulkMoveObjectOperation.ObjectMove move : operation.moves()) {
                if (move.objectId() == null) {
                    throw new IllegalArgumentException("Object ID cannot be null in bulk move");
                }

                CanvasObject object = session.getObject(move.objectId());
                if (object == null) {
                    throw new ObjectNotFoundException(move.objectId());
                }

                if (move.expectedVersion() != null && !Objects.equals(object.getVersion(), move.expectedVersion())) {
                    throw new VersionMismatchException(move.expectedVersion(), object.getVersion());
                }

                resolved.add(object);
            }

            // Second pass: all moves validated, now apply them.
            for (int i = 0; i < resolved.size(); i++) {
                CanvasObject object = resolved.get(i);
                BulkMoveObjectOperation.ObjectMove move = operation.moves().get(i);

                object.setX(move.x());
                object.setY(move.y());
                object.setVersion(object.getVersion() != null ? object.getVersion() + 1 : 1L);

                log.debug("Moved object {} to ({}, {}) on board {}", move.objectId(), move.x(), move.y(), operation.boardId());
            }

            session.incrementVersion();
            session.touch();

            dirtySessionTracker.markDirty(operation.boardId());

            log.info("Successfully bulk moved {} objects on board {}", operation.moves().size(), operation.boardId());

        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}
