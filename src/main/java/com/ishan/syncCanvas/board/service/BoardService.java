package com.ishan.syncCanvas.board.service;

import com.ishan.syncCanvas.board.dto.BoardResponse;
import com.ishan.syncCanvas.board.dto.CreateBoardRequest;
import com.ishan.syncCanvas.board.dto.UpdateBoardRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable; // Add this import

// import java.util.List;
import java.util.UUID;

public interface BoardService {
    BoardResponse createBoard(CreateBoardRequest request);

    Page<BoardResponse> getBoards(String name, Pageable pageable);

    BoardResponse getBoardById(UUID id);

    void deleteBoard(UUID id);

    BoardResponse updateBoard(
            UUID id,
            UpdateBoardRequest request);
}
