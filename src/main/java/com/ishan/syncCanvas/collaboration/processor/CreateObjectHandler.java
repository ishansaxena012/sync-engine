package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.operation.CreateObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
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
    public void handle(CreateObjectOperation operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();

        try {
            CreateCanvasObjectRequest request = operation.canvasObject();
            if (request == null) {
                throw new IllegalArgumentException("Create object request cannot be null");
            }
            UUID objectId = request.getId() != null ? request.getId() : UUID.randomUUID();
            request.setId(objectId);

            CanvasObject object = CanvasObject.builder()
                    .id(objectId)
                    .boardId(request.getBoardId())
                    .type(request.getType())
                    .x(request.getX())
                    .y(request.getY())
                    .rotation(request.getRotation())
                    .zindex(request.getZindex())
                    .payload(request.getPayload())
                    .createdBy(operation.userId())
                    .build();

            session.addObject(object);

            dirtySessionTracker.markDirty(operation.boardId());

            log.debug(
                    "Canvas object {} created on board {}",
                    object.getId(),
                    operation.boardId());

        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}