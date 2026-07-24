package com.ishan.syncCanvas.board.service.impl;

import com.ishan.syncCanvas.board.dto.BoardResponse;
import com.ishan.syncCanvas.board.dto.CreateBoardRequest;
import com.ishan.syncCanvas.board.dto.UpdateBoardRequest;
import com.ishan.syncCanvas.board.entity.Board;
import com.ishan.syncCanvas.board.entity.Visibility;
import com.ishan.syncCanvas.board.mapper.BoardMapper;
import com.ishan.syncCanvas.board.repository.BoardRepository;
import com.ishan.syncCanvas.board.service.BoardService;
import com.ishan.syncCanvas.common.exception.BoardNotFoundException;
// import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardServiceImpl.class);
    private final BoardRepository boardRepository;

    @Override
    public BoardResponse createBoard(CreateBoardRequest request) {
        log.info("Creating board with name: {}", request.getName());
        Board board = Board.builder()
                .name(request.getName())
                .ownerId(UUID.randomUUID())
                .visibility(Visibility.PRIVATE)
                .build();
        Board savedBoard = boardRepository.save(board);
        log.info("Board created successfully. id={}", savedBoard.getId());

        return BoardMapper.toResponse(savedBoard);
    }

    @Override
    public Page<BoardResponse> getBoards(
            String name,
            Pageable pageable) {

        log.debug("Fetching boards. Search={}", name);

        Page<Board> boards;

        if (name == null || name.isBlank()) {
            boards = boardRepository.findAll(pageable);
        } else {
            boards = boardRepository.findByNameContainingIgnoreCase(
                    name,
                    pageable);
        }

        return boards.map(BoardMapper::toResponse);
    }

    @Override
    public BoardResponse getBoardById(UUID id) {

        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new BoardNotFoundException("Board not found"));

        return BoardMapper.toResponse(board);
    }

    @Override
    @Transactional
    public void deleteBoard(UUID id) {

        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new BoardNotFoundException("Board not found"));

        boardRepository.delete(board);
    }

    @Override
    @Transactional
    public BoardResponse updateBoard(
            UUID id,
            UpdateBoardRequest request) {

        log.info("Updating board {}", id);

        Board board = boardRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Board {} not found", id);
                    return new BoardNotFoundException(id);
                });

        if (request.getName() != null) {
            board.setName(request.getName());
        }

        if (request.getVisibility() != null) {
            board.setVisibility(request.getVisibility());
        }

        Board updatedBoard = boardRepository.save(board);

        log.info("Board {} updated successfully", id);

        return BoardMapper.toResponse(updatedBoard);
    }
}