package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.Operation;
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

    public void process(Operation operation) {

        OperationHandler<? extends Operation> handler = registry.get(operation.getClass());

        if (handler == null) {
            throw new IllegalArgumentException(
                    "No handler registered for operation: "
                            + operation.getClass().getSimpleName());
        }

        dispatch(handler, operation);
    }

    @SuppressWarnings("unchecked")
    private <T extends Operation> void dispatch(
            OperationHandler<? extends Operation> handler,
            Operation operation) {
        ((OperationHandler<T>) handler).handle((T) operation);
    }
}