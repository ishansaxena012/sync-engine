package com.ishan.syncCanvas.collaboration.event;

import com.ishan.syncCanvas.collaboration.operation.Operation;

public record BoardEvent(
        String type,
        Operation operation) {

}
