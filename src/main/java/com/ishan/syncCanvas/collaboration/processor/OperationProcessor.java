package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.undo.UndoableChange;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class OperationProcessor {

    private final List<OperationHandler<? extends Operation>> handlers;
    private final Map<Class<? extends Operation>, OperationHandler<? extends Operation>> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerHandlers() {

        handlers.forEach(handler -> {

            OperationHandler<? extends Operation> existing = registry.putIfAbsent(handler.supports(), handler);

            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate handler registered for operation: "
                                + handler.supports().getSimpleName());
            }
        });
    }

    public UndoableChange process(Operation operation) {

        OperationHandler<? extends Operation> handler = registry.get(operation.getClass());

        if (handler == null) {
            throw new IllegalArgumentException(
                    "No handler registered for operation: "
                            + operation.getClass().getSimpleName());
        }

        return dispatch(handler, operation);
    }

    /**
     * Applies an operation to an arbitrary session (e.g. a throwaway one during board
     * reconstruction, or the live session during an undo/redo) without touching the live
     * session registry or dirty tracking.
     */
    public UndoableChange apply(Operation operation, BoardSession session, ApplyMode mode) {

        OperationHandler<? extends Operation> handler = registry.get(operation.getClass());

        if (handler == null) {
            throw new IllegalArgumentException(
                    "No handler registered for operation: "
                            + operation.getClass().getSimpleName());
        }

        return dispatchApply(handler, operation, session, mode);
    }

    @SuppressWarnings("unchecked")
    private <T extends Operation> UndoableChange dispatch(
            OperationHandler<? extends Operation> handler,
            Operation operation) {
        return ((OperationHandler<T>) handler).handle((T) operation);
    }

    @SuppressWarnings("unchecked")
    private <T extends Operation> UndoableChange dispatchApply(
            OperationHandler<? extends Operation> handler,
            Operation operation,
            BoardSession session,
            ApplyMode mode) {
        return ((OperationHandler<T>) handler).apply((T) operation, session, mode);
    }
}