package com.ishan.syncCanvas.collaboration.undo;

/**
 * Whether a {@link BoardUndoStackEntry}'s forward action is currently in effect on the
 * board ({@code ACTIVE}) or has been reversed ({@code UNDONE}, and so is available to
 * redo). A user's per-board undo stack is exactly its entries in
 * {@code stackPosition} order; {@link BoardUndoCursor#getTopPosition()} is how many of
 * them, counting from position 1, are currently {@code ACTIVE}.
 */
public enum UndoStackStatus {
    ACTIVE,
    UNDONE
}
