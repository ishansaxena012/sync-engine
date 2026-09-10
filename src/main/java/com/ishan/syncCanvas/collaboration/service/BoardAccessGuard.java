package com.ishan.syncCanvas.collaboration.service;

import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.entity.Visibility;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Confirms an authenticated caller may access a board (owner, or public visibility).
 * Shared by the canvas-operations, cursor, presence and sync pipelines so all enforce
 * the exact same rule instead of maintaining multiple authorization implementations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardAccessGuard {

    private final BoardRepository boardRepository;

    public void assertAccessible(UUID boardId, UUID userId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardAccessDeniedException("Board not found: " + boardId));

        if (!board.getOwnerId().equals(userId) && board.getVisibility() != Visibility.PUBLIC) {
            throw new BoardAccessDeniedException("You do not have access to board " + boardId);
        }
    }

    /** Non-throwing variant for best-effort channels that reject by dropping rather than erroring. */
    public boolean isAccessible(UUID boardId, UUID userId) {
        try {
            assertAccessible(boardId, userId);
            return true;
        } catch (BoardAccessDeniedException ex) {
            log.warn("Access check failed for user {} on board {}: {}", userId, boardId, ex.getMessage());
            return false;
        }
    }
}
