package com.ishan.syncCanvas.collaboration.undo;

/**
 * Outcome of one undo or redo attempt. {@code NOTHING_TO_UNDO}/{@code NOTHING_TO_REDO}
 * and {@code CONFLICT} are ordinary, expected outcomes — not errors — reported back to
 * the client as normal responses rather than thrown as exceptions.
 */
public record UndoRedoResult(UndoRedoStatus status, Long sourceSequence, Long sequence) {

    public static UndoRedoResult ok(long sourceSequence, long sequence) {
        return new UndoRedoResult(UndoRedoStatus.OK, sourceSequence, sequence);
    }

    public static UndoRedoResult nothingToUndo() {
        return new UndoRedoResult(UndoRedoStatus.NOTHING_TO_UNDO, null, null);
    }

    public static UndoRedoResult nothingToRedo() {
        return new UndoRedoResult(UndoRedoStatus.NOTHING_TO_REDO, null, null);
    }

    public static UndoRedoResult conflict(long sourceSequence) {
        return new UndoRedoResult(UndoRedoStatus.CONFLICT, sourceSequence, null);
    }
}
