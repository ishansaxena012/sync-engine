package com.ishan.syncCanvas.board.controller;

import com.ishan.syncCanvas.board.dto.BoardResponse;
import com.ishan.syncCanvas.board.dto.CreateBoardRequest;
import com.ishan.syncCanvas.board.dto.UpdateBoardRequest;
import com.ishan.syncCanvas.board.service.BoardService;
import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

        private final BoardService boardService;

        @PostMapping
        public ResponseEntity<ApiResponse<BoardResponse>> createBoard(
                        @Valid @RequestBody CreateBoardRequest request) {

                BoardResponse response = boardService.createBoard(request);

                return ResponseUtil.created(
                                "Board created successfully",
                                response);
        }

        @GetMapping
        public ResponseEntity<ApiResponse<Page<BoardResponse>>> getBoards(

                        @RequestParam(required = false) String name,

                        Pageable pageable) {

                return ResponseUtil.success(
                                boardService.getBoards(name, pageable),
                                "Boards fetched successfully",
                                HttpStatus.OK);
        }

        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<BoardResponse>> getBoard(
                        @PathVariable UUID id) {
                BoardResponse response = boardService.getBoardById(id);
                return ResponseUtil.success(
                                response,
                                "Boards fetched successfully",
                                HttpStatus.OK);
        }

        @DeleteMapping("{id}")
        public ResponseEntity<Void> deleteBoard(
                        @PathVariable UUID id) {
                boardService.deleteBoard(id);
                return ResponseEntity.noContent().build();
        }

        @PatchMapping("/{id}")
        public ResponseEntity<ApiResponse<BoardResponse>> updateBoard(
                        @PathVariable UUID id,
                        @Valid @RequestBody UpdateBoardRequest request) {

                return ResponseUtil.success(
                                boardService.updateBoard(id, request),
                                "Board updated successfully",
                                HttpStatus.OK);
        }
}