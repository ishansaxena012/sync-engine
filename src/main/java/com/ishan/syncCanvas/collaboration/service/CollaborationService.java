package com.ishan.syncCanvas.collaboration.service;

import java.util.*;
import com.ishan.syncCanvas.collaboration.operation.Operation;

public interface CollaborationService {

    void processOperation(
            UUID boardId,
            Operation operation);

}