package com.ishan.syncCanvas.collaboration.processor;

import org.springframework.stereotype.Component;

import com.ishan.syncCanvas.collaboration.operation.ChangePayloadOperation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChangePayloadHandler
        implements OperationHandler<ChangePayloadOperation> {

    private final BoardSessionManager sessionManager;

    @Override
    public Class<ChangePayloadOperation> supports() {
        return ChangePayloadOperation.class;
    }

    @Override
    public void handle(ChangePayloadOperation operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow();

        session.getLock().writeLock().lock();

        try {

            // TODO:
            // update payload

            log.debug("CHANGE_PAYLOAD executed");

        } finally {
            session.getLock().writeLock().unlock();
        }
    }
}
