package com.ishan.syncCanvas.collaboration.undo;

import java.util.UUID;

import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.domain.CanvasPayload;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;

/**
 * Everything needed to recreate a {@link CanvasObject} exactly, independent of the JPA
 * entity — captured once, at the moment an object is created or deleted, so a later
 * undo/redo can rebuild it without touching Hibernate-managed state.
 */
public record CanvasObjectSnapshot(
        UUID id,
        CanvasObjectType type,
        double x,
        double y,
        double rotation,
        int zindex,
        CanvasPayload payload,
        UUID createdBy) {

    public static CanvasObjectSnapshot of(CanvasObject object) {
        return new CanvasObjectSnapshot(
                object.getId(),
                object.getType(),
                object.getX(),
                object.getY(),
                object.getRotation(),
                object.getZindex(),
                object.getPayload(),
                object.getCreatedBy());
    }
}
