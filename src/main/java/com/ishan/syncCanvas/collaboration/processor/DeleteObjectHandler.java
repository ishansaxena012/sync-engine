package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.DeleteObjectOperation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteObjectHandler
        implements OperationHandler<DeleteObjectOperation> {

    private final BoardSessionManager sessionManager;

    @Override
    public Class<DeleteObjectOperation> supports() {
        return DeleteObjectOperation.class;
    }

    @Override
    public void handle(DeleteObjectOperation operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active session found for board: "
                                + operation.boardId()));

        session.getLock().writeLock().lock();

        try {

            boolean removed = session.removeObject(operation.objectId());

            if (!removed) {
                throw new IllegalArgumentException(
                        "Canvas object not found: " + operation.objectId());
            }

            log.debug(
                    "Deleted object {} from board {}",
                    operation.objectId(),
                    operation.boardId());

        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}