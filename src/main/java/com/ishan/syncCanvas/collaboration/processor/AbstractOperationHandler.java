package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;

public abstract class AbstractOperationHandler<T extends Operation>
        implements OperationHandler<T> {

    protected final BoardSessionManager sessionManager;

    protected AbstractOperationHandler(BoardSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public final void handle(T operation) {

        BoardSession session = sessionManager
                .getSession(operation.boardId())
                .orElseThrow();

        session.getLock().writeLock().lock();

        try {

            execute(session, operation);

        } finally {

            session.getLock().writeLock().unlock();

        }
    }

    protected abstract void execute(
            BoardSession session,
            T operation);
}