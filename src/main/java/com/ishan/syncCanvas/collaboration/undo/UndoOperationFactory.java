package com.ishan.syncCanvas.collaboration.undo;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.collaboration.operation.BulkMoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.ChangePayloadOperation;
import com.ishan.syncCanvas.collaboration.operation.CreateObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.DeleteObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.operation.RotateObjectOperation;
import com.ishan.syncCanvas.collaboration.processor.ApplyMode;
import com.ishan.syncCanvas.collaboration.session.BoardSession;

/**
 * Builds the concrete {@link Operation} an undo or redo actually applies, from a stored
 * {@link UndoableChange} plus the version(s) the affected object(s) are verified to
 * currently be at. Never trusts anything from a client here — every field comes from the
 * durable stack entry or the live session.
 */
public final class UndoOperationFactory {

    private UndoOperationFactory() {
    }

    /** The inverse of the change — what an UNDO applies. */
    public static Operation buildUndoOperation(UndoableChange change, UUID boardId, UUID userId, Map<UUID, Long> versions) {
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();
        return switch (change) {
            case UndoableChange.CreateChange c -> new DeleteObjectOperation(
                    operationId, boardId, userId, c.created().id(), versions.get(c.created().id()), now);
            case UndoableChange.DeleteChange d -> new CreateObjectOperation(
                    operationId, boardId, userId, toCreateRequest(d.deleted()), now);
            case UndoableChange.MoveChange m -> new MoveObjectOperation(
                    operationId, boardId, userId, now, m.objectId(), versions.get(m.objectId()), m.oldX(), m.oldY());
            case UndoableChange.RotateChange r -> new RotateObjectOperation(
                    operationId, boardId, userId, now, r.objectId(), versions.get(r.objectId()), r.oldRotation());
            case UndoableChange.ChangePayloadChange p -> new ChangePayloadOperation(
                    operationId, boardId, userId, now, p.objectId(), versions.get(p.objectId()), p.oldPayload());
            case UndoableChange.BulkMoveChange b -> new BulkMoveObjectOperation(
                    operationId, boardId, userId, now,
                    b.moves().stream()
                            .map(mv -> new BulkMoveObjectOperation.ObjectMove(
                                    mv.objectId(), versions.get(mv.objectId()), mv.oldX(), mv.oldY()))
                            .toList());
        };
    }

    /** The change re-applied as originally made — what a REDO applies. */
    public static Operation buildRedoOperation(UndoableChange change, UUID boardId, UUID userId, Map<UUID, Long> versions) {
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();
        return switch (change) {
            case UndoableChange.CreateChange c -> new CreateObjectOperation(
                    operationId, boardId, userId, toCreateRequest(c.created()), now);
            case UndoableChange.DeleteChange d -> new DeleteObjectOperation(
                    operationId, boardId, userId, d.deleted().id(), versions.get(d.deleted().id()), now);
            case UndoableChange.MoveChange m -> new MoveObjectOperation(
                    operationId, boardId, userId, now, m.objectId(), versions.get(m.objectId()), m.newX(), m.newY());
            case UndoableChange.RotateChange r -> new RotateObjectOperation(
                    operationId, boardId, userId, now, r.objectId(), versions.get(r.objectId()), r.newRotation());
            case UndoableChange.ChangePayloadChange p -> new ChangePayloadOperation(
                    operationId, boardId, userId, now, p.objectId(), versions.get(p.objectId()), p.newPayload());
            case UndoableChange.BulkMoveChange b -> new BulkMoveObjectOperation(
                    operationId, boardId, userId, now,
                    b.moves().stream()
                            .map(mv -> new BulkMoveObjectOperation.ObjectMove(
                                    mv.objectId(), versions.get(mv.objectId()), mv.newX(), mv.newY()))
                            .toList());
        };
    }

    /**
     * CREATE always needs id-trust: recreating a previously-deleted object (or redoing a
     * previously-undone create) must reuse its exact original id, which
     * {@code CreateObjectHandler} only honors in {@link ApplyMode#REPLAY}. Every other
     * operation type goes through {@link ApplyMode#LIVE} so its {@code expectedVersion}
     * is defensively re-checked at the point of application too, even though the caller
     * has already verified it against the stack entry under lock.
     */
    public static ApplyMode applyModeFor(Operation operation) {
        return operation instanceof CreateObjectOperation ? ApplyMode.REPLAY : ApplyMode.LIVE;
    }

    /**
     * The version each affected object is actually at right now (or
     * {@link BoardUndoStackEntry#NOT_EXISTS} if it doesn't exist), read from the live
     * session. Compared against a stack entry's stored {@code expectedVersions} to detect
     * whether anything else has touched the object(s) since this entry last settled.
     */
    public static Map<UUID, Long> currentVersions(BoardSession session, List<UUID> objectIds) {
        Map<UUID, Long> versions = new HashMap<>();
        for (UUID id : objectIds) {
            var object = session.getObject(id);
            versions.put(id, object != null
                    ? (object.getVersion() != null ? object.getVersion() : 1L)
                    : BoardUndoStackEntry.NOT_EXISTS);
        }
        return versions;
    }

    private static CreateCanvasObjectRequest toCreateRequest(CanvasObjectSnapshot snapshot) {
        return CreateCanvasObjectRequest.builder()
                .id(snapshot.id())
                .boardId(null)
                .type(snapshot.type())
                .x(snapshot.x())
                .y(snapshot.y())
                .rotation(snapshot.rotation())
                .zindex(snapshot.zindex())
                .payload(snapshot.payload())
                .createdBy(snapshot.createdBy())
                .build();
    }
}
