package com.ishan.syncCanvas.board.service;

import com.ishan.syncCanvas.board.dto.BoardResponse;
import com.ishan.syncCanvas.board.dto.CreateBoardRequest;
import com.ishan.syncCanvas.board.dto.UpdateBoardRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable; // Add this import

// import java.util.List;
import java.util.UUID;

public interface BoardService {
    BoardResponse createBoard(UUID ownerId, CreateBoardRequest request);

    Page<BoardResponse> getBoards(UUID userId, String name, Pageable pageable);

    BoardResponse getBoardById(UUID userId, UUID id);

    void deleteBoard(UUID userId, UUID id);

    BoardResponse updateBoard(
            UUID userId,
            UUID id,
            UpdateBoardRequest request);
}
