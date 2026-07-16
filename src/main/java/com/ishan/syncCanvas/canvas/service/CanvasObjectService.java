package com.ishan.syncCanvas.canvas.service;

import com.ishan.syncCanvas.canvas.domain.CanvasPayload;
import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import java.util.List;
import java.util.UUID;

public interface CanvasObjectService {

    CanvasObjectResponse createObject(CreateCanvasObjectRequest request);

    List<CanvasObjectResponse> getObjectsByBoard(UUID boardId);

    void deleteObject(UUID objectId);

    // Operation-specific update methods
    CanvasObjectResponse moveObject(UUID objectId, double x, double y, int zindex);

    CanvasObjectResponse rotateObject(UUID objectId, double rotation);

    CanvasObjectResponse changePayload(UUID objectId, CanvasPayload newPayload);
}
