package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.operation.CreateObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import com.ishan.syncCanvas.collaboration.undo.CanvasObjectSnapshot;
import com.ishan.syncCanvas.collaboration.undo.UndoableChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateObjectHandler
        implements OperationHandler<CreateObjectOperation> {

    private final BoardSessionManager sessionManager;
    private final DirtySessionTracker dirtySessionTracker;

    @Override
    public Class<CreateObjectOperation> supports() {
        return CreateObjectOperation.class;
    }

    @Override
    public UndoableChange handle(CreateObjectOperation operation) {

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
    public UndoableChange apply(CreateObjectOperation operation, BoardSession session, ApplyMode mode) {
        CreateCanvasObjectRequest request = operation.canvasObject();
        if (request == null) {
            throw new IllegalArgumentException("Create object request cannot be null");
        }

        // LIVE: ids are always server-generated — a client-supplied id could collide with
        // (and overwrite) an existing object on another board once persisted.
        // REPLAY: the id in the stored event IS the server-assigned one from the original
        // accept, and must be reused so later events referencing it still resolve.
        UUID objectId = mode == ApplyMode.REPLAY && request.getId() != null
                ? request.getId()
                : UUID.randomUUID();
        request.setId(objectId);
        request.setBoardId(operation.boardId());
        request.setCreatedBy(operation.userId());

        CanvasObject object = CanvasObject.builder()
                .id(objectId)
                .boardId(operation.boardId())
                .type(request.getType())
                .x(request.getX())
                .y(request.getY())
                .rotation(request.getRotation())
                .zindex(request.getZindex() != null ? request.getZindex() : 0)
                .payload(request.getPayload())
                .createdBy(operation.userId())
                .version(1L)
                .build();

        session.addObject(object);

        log.debug("Canvas object {} created on board {} ({})", object.getId(), operation.boardId(), mode);

        return new UndoableChange.CreateChange(CanvasObjectSnapshot.of(object));
    }
}
