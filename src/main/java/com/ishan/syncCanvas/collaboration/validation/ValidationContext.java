package com.ishan.syncCanvas.collaboration.validation;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
// import java.util.UUID;

public record ValidationContext(
        BoardSession session,
        CanvasObject object) {

    // public ValidationContext validateObjectOperation(
    // UUID boardId,
    // UUID objectId,
    // Long expectedVersion) {

    // }
}
