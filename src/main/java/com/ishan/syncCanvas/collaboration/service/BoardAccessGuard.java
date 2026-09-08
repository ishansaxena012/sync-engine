package com.ishan.syncCanvas.collaboration.service;

import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.entity.Visibility;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Confirms an authenticated caller may access a board (owner, or public visibility).
 * Shared by the canvas-operations pipeline and the cursor pipeline so both enforce the
 * exact same rule instead of maintaining two authorization implementations.
 */
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
}
