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
import com.ishan.syncCanvas.user.service.UserService;
import com.ishan.syncCanvas.user.dto.UserProfileResponse;
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
    private final UserService userService;

    private BoardResponse mapToResponse(Board board) {
        UserProfileResponse owner = userService.getUserProfile(board.getOwnerId());
        return BoardMapper.toResponse(board, owner);
    }

    @Override
    public BoardResponse createBoard(UUID ownerId, CreateBoardRequest request) {
        log.info("Creating board with name: {}, visibility: {}", request.getName(), request.getVisibility());
        Visibility visibility = request.getVisibility() != null ? request.getVisibility() : Visibility.PRIVATE;
        Board board = Board.builder()
                .name(request.getName())
                .ownerId(ownerId)
                .visibility(visibility)
                .build();
        Board savedBoard = boardRepository.save(board);
        log.info("Board created successfully. id={}", savedBoard.getId());

        return mapToResponse(savedBoard);
    }

    @Override
    public Page<BoardResponse> getBoards(
            UUID userId,
            String name,
            Pageable pageable) {

        log.debug("Fetching boards for user={}. Search={}", userId, name);

        Page<Board> boards;

        if (name == null || name.isBlank()) {
            boards = boardRepository.findAccessibleBoards(userId, pageable);
        } else {
            boards = boardRepository.findAccessibleBoardsByName(
                    userId,
                    name,
                    pageable);
        }

        return boards.map(this::mapToResponse);
    }

    @Override
    public BoardResponse getBoardById(UUID userId, UUID id) {

        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new BoardNotFoundException("Board not found"));

        if (!board.getOwnerId().equals(userId) && board.getVisibility() != Visibility.PUBLIC) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to access this board");
        }

        return mapToResponse(board);
    }

    @Override
    @Transactional
    public void deleteBoard(UUID userId, UUID id) {

        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new BoardNotFoundException("Board not found"));

        if (!board.getOwnerId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to delete this board");
        }

        boardRepository.delete(board);
    }

    @Override
    @Transactional
    public BoardResponse updateBoard(
            UUID userId,
            UUID id,
            UpdateBoardRequest request) {

        log.info("Updating board {}", id);

        Board board = boardRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Board {} not found", id);
                    return new BoardNotFoundException(id);
                });

        if (!board.getOwnerId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to update this board");
        }

        if (request.getName() != null) {
            board.setName(request.getName());
        }

        if (request.getVisibility() != null) {
            board.setVisibility(request.getVisibility());
        }

        Board updatedBoard = boardRepository.save(board);

        log.info("Board {} updated successfully", id);

        return mapToResponse(updatedBoard);
    }
}