package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.Operation;

public interface OperationHandler<T extends Operation> {

    Class<T> supports();

    void handle(T operation);
}