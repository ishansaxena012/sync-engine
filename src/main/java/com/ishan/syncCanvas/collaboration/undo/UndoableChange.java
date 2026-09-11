package com.ishan.syncCanvas.collaboration.undo;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What one applied operation actually changed, captured at apply time so it can later be
 * reversed (undo) or re-applied (redo) without re-deriving it from anything else. Every
 * permitted type carries enough to build both the forward and inverse {@code Operation}
 * for the object(s) it touched — old and new values, never a diff.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "changeType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UndoableChange.CreateChange.class, name = "CREATE"),
        @JsonSubTypes.Type(value = UndoableChange.DeleteChange.class, name = "DELETE"),
        @JsonSubTypes.Type(value = UndoableChange.MoveChange.class, name = "MOVE"),
        @JsonSubTypes.Type(value = UndoableChange.RotateChange.class, name = "ROTATE"),
        @JsonSubTypes.Type(value = UndoableChange.ChangePayloadChange.class, name = "CHANGE_PAYLOAD"),
        @JsonSubTypes.Type(value = UndoableChange.BulkMoveChange.class, name = "BULK_MOVE")
})
public sealed interface UndoableChange {

    /** Every object id this change touches, in a stable order. */
    List<UUID> affectedObjectIds();

    record CreateChange(CanvasObjectSnapshot created) implements UndoableChange {
        @Override
        public List<UUID> affectedObjectIds() {
            return List.of(created.id());
        }
    }

    record DeleteChange(CanvasObjectSnapshot deleted) implements UndoableChange {
        @Override
        public List<UUID> affectedObjectIds() {
            return List.of(deleted.id());
        }
    }

    record MoveChange(UUID objectId, double oldX, double oldY, double newX, double newY) implements UndoableChange {
        @Override
        public List<UUID> affectedObjectIds() {
            return List.of(objectId);
        }
    }

    record RotateChange(UUID objectId, double oldRotation, double newRotation) implements UndoableChange {
        @Override
        public List<UUID> affectedObjectIds() {
            return List.of(objectId);
        }
    }

    record ChangePayloadChange(
            UUID objectId,
            com.ishan.syncCanvas.canvas.domain.CanvasPayload oldPayload,
            com.ishan.syncCanvas.canvas.domain.CanvasPayload newPayload) implements UndoableChange {
        @Override
        public List<UUID> affectedObjectIds() {
            return List.of(objectId);
        }
    }

    record BulkMoveChange(List<SingleMove> moves) implements UndoableChange {
        @Override
        public List<UUID> affectedObjectIds() {
            return moves.stream().map(SingleMove::objectId).toList();
        }

        public record SingleMove(UUID objectId, double oldX, double oldY, double newX, double newY) {
        }
    }
}
