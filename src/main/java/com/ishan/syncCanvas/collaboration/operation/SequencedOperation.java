package com.ishan.syncCanvas.collaboration.operation;

/**
 * A board-accepted {@link Operation} paired with its server-assigned, per-board
 * monotonic sequence number. This is the shape actually broadcast to
 * {@code /topic/boards/{boardId}} and stored in the replay buffer — clients never
 * supply a sequence themselves; it only ever appears on the way out.
 *
 * <p>Wraps rather than extends the existing {@link Operation} records so the six
 * concrete operation types stay untouched — sequencing is a publish-time concern, not
 * part of an operation's own identity.
 */
public record SequencedOperation(long sequence, Operation operation) {
}
