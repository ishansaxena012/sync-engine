package com.ishan.syncCanvas.collaboration.cursor;

import java.util.List;
import java.util.UUID;

/**
 * Deterministic, centralized color assignment for cursors: same user always maps to
 * the same color (hash of their ID into a fixed palette), no random generation per
 * event, no extra DB column.
 */
public final class CursorColorPalette {

    private static final List<String> COLORS = List.of(
            "#E57373", "#F06292", "#BA68C8", "#9575CD",
            "#7986CB", "#64B5F6", "#4DB6AC", "#81C784",
            "#DCE775", "#FFD54F", "#FFB74D", "#A1887F"
    );

    private CursorColorPalette() {
    }

    public static String colorFor(UUID userId) {
        int index = Math.floorMod(userId.hashCode(), COLORS.size());
        return COLORS.get(index);
    }
}
