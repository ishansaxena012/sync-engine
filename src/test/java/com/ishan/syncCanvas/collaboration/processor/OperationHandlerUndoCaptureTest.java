package com.ishan.syncCanvas.collaboration.processor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.domain.RectanglePayload;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.VersionMismatchException;
import com.ishan.syncCanvas.collaboration.operation.BulkMoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.ChangePayloadOperation;
import com.ishan.syncCanvas.collaboration.operation.CreateObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.DeleteObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.RotateObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import com.ishan.syncCanvas.collaboration.undo.UndoableChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every handler's {@code apply} must both perform the mutation and return an
 * {@link UndoableChange} that exactly captures what it changed — the raw material
 * undo/redo builds on. Also covers the {@code DeleteObjectHandler} expectedVersion
 * check: it used to be the one handler of the five versioned operation types that never
 * validated it, silently letting a stale-version delete discard a concurrent edit.
 */
@ExtendWith(MockitoExtension.class)
class OperationHandlerUndoCaptureTest {

    @Mock
    private BoardSessionManager sessionManager;
    @Mock
    private DirtySessionTracker dirtySessionTracker;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private BoardSession session;

    @BeforeEach
    void setUp() {
        session = new BoardSession(boardId);
    }

    private CanvasObject objectAt(double x, double y, long version) {
        CanvasObject object = CanvasObject.builder()
                .id(UUID.randomUUID())
                .boardId(boardId)
                .type(CanvasObjectType.RECTANGLE)
                .x(x).y(y).rotation(0).zindex(0)
                .payload(new RectanglePayload(10, 10, "#fff", "#000", 1, 0))
                .createdBy(userId)
                .version(version)
                .build();
        session.initialize(List.of(object));
        return object;
    }

    @Test
    void createCapturesTheCreatedObjectAsTheChange() {
        CreateObjectHandler handler = new CreateObjectHandler(sessionManager, dirtySessionTracker);
        CreateCanvasObjectRequest request = CreateCanvasObjectRequest.builder()
                .type(CanvasObjectType.CIRCLE)
                .x(1).y(2).rotation(0).zindex(0)
                .payload(new RectanglePayload(5, 5, "#111", "#222", 1, 0))
                .build();
        CreateObjectOperation op = new CreateObjectOperation(UUID.randomUUID(), boardId, userId, request, Instant.now());

        UndoableChange change = handler.apply(op, session, ApplyMode.LIVE);

        assertThat(change).isInstanceOf(UndoableChange.CreateChange.class);
        UndoableChange.CreateChange created = (UndoableChange.CreateChange) change;
        assertThat(created.created().x()).isEqualTo(1);
        assertThat(created.created().y()).isEqualTo(2);
        assertThat(session.getObject(created.created().id())).isNotNull();
    }

    @Test
    void deleteRejectsAStaleExpectedVersionInsteadOfSilentlyDiscardingANewerEdit() {
        DeleteObjectHandler handler = new DeleteObjectHandler(sessionManager, dirtySessionTracker);
        CanvasObject object = objectAt(1, 1, 5L);
        DeleteObjectOperation op = new DeleteObjectOperation(
                UUID.randomUUID(), boardId, userId, object.getId(), 4L, Instant.now());

        assertThatThrownBy(() -> handler.apply(op, session, ApplyMode.LIVE))
                .isInstanceOf(VersionMismatchException.class);
        assertThat(session.getObject(object.getId())).isNotNull();
    }

    @Test
    void deleteCapturesTheDeletedObjectsPriorStateAsTheChange() {
        DeleteObjectHandler handler = new DeleteObjectHandler(sessionManager, dirtySessionTracker);
        CanvasObject object = objectAt(3, 4, 2L);
        DeleteObjectOperation op = new DeleteObjectOperation(
                UUID.randomUUID(), boardId, userId, object.getId(), 2L, Instant.now());

        UndoableChange change = handler.apply(op, session, ApplyMode.LIVE);

        assertThat(change).isInstanceOf(UndoableChange.DeleteChange.class);
        UndoableChange.DeleteChange deleted = (UndoableChange.DeleteChange) change;
        assertThat(deleted.deleted().id()).isEqualTo(object.getId());
        assertThat(deleted.deleted().x()).isEqualTo(3);
        assertThat(deleted.deleted().y()).isEqualTo(4);
        assertThat(session.getObject(object.getId())).isNull();
    }

    @Test
    void moveCapturesOldAndNewPosition() {
        MoveObjectHandler handler = new MoveObjectHandler(sessionManager, dirtySessionTracker);
        CanvasObject object = objectAt(1, 1, 1L);
        MoveObjectOperation op = new MoveObjectOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(), object.getId(), 1L, 9, 9);

        UndoableChange change = handler.apply(op, session, ApplyMode.LIVE);

        assertThat(change).isEqualTo(new UndoableChange.MoveChange(object.getId(), 1, 1, 9, 9));
        assertThat(object.getVersion()).isEqualTo(2L);
    }

    @Test
    void rotateCapturesOldAndNewRotation() {
        RotateObjectHandler handler = new RotateObjectHandler(sessionManager, dirtySessionTracker);
        CanvasObject object = objectAt(0, 0, 1L);
        RotateObjectOperation op = new RotateObjectOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(), object.getId(), 1L, 90);

        UndoableChange change = handler.apply(op, session, ApplyMode.LIVE);

        assertThat(change).isEqualTo(new UndoableChange.RotateChange(object.getId(), 0, 90));
    }

    @Test
    void changePayloadCapturesOldAndNewPayload() {
        ChangePayloadHandler handler = new ChangePayloadHandler(sessionManager, dirtySessionTracker);
        CanvasObject object = objectAt(0, 0, 1L);
        var oldPayload = object.getPayload();
        var newPayload = new RectanglePayload(20, 20, "#abc", "#def", 2, 1);
        ChangePayloadOperation op = new ChangePayloadOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(), object.getId(), 1L, newPayload);

        UndoableChange change = handler.apply(op, session, ApplyMode.LIVE);

        assertThat(change).isEqualTo(new UndoableChange.ChangePayloadChange(object.getId(), oldPayload, newPayload));
    }

    @Test
    void bulkMoveCapturesEveryObjectsOldAndNewPositionAtomically() {
        BulkMoveObjectHandler handler = new BulkMoveObjectHandler(sessionManager, dirtySessionTracker);
        CanvasObject a = objectAt(1, 1, 1L);
        CanvasObject b = objectAt(2, 2, 1L);
        BulkMoveObjectOperation op = new BulkMoveObjectOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(),
                List.of(new BulkMoveObjectOperation.ObjectMove(a.getId(), 1L, 5, 5),
                        new BulkMoveObjectOperation.ObjectMove(b.getId(), 1L, 6, 6)));

        UndoableChange change = handler.apply(op, session, ApplyMode.LIVE);

        assertThat(change).isInstanceOf(UndoableChange.BulkMoveChange.class);
        UndoableChange.BulkMoveChange bulk = (UndoableChange.BulkMoveChange) change;
        assertThat(bulk.moves()).containsExactlyInAnyOrder(
                new UndoableChange.BulkMoveChange.SingleMove(a.getId(), 1, 1, 5, 5),
                new UndoableChange.BulkMoveChange.SingleMove(b.getId(), 2, 2, 6, 6));
    }

    @Test
    void bulkMoveRejectsTheWholeBatchIfAnySingleMoveIsStale() {
        BulkMoveObjectHandler handler = new BulkMoveObjectHandler(sessionManager, dirtySessionTracker);
        CanvasObject a = objectAt(1, 1, 1L);
        CanvasObject b = objectAt(2, 2, 5L);
        BulkMoveObjectOperation op = new BulkMoveObjectOperation(
                UUID.randomUUID(), boardId, userId, Instant.now(),
                List.of(new BulkMoveObjectOperation.ObjectMove(a.getId(), 1L, 5, 5),
                        new BulkMoveObjectOperation.ObjectMove(b.getId(), 4L, 6, 6)));

        assertThatThrownBy(() -> handler.apply(op, session, ApplyMode.LIVE))
                .isInstanceOf(VersionMismatchException.class);
        assertThat(a.getX()).isEqualTo(1);
        assertThat(a.getVersion()).isEqualTo(1L);
    }
}
