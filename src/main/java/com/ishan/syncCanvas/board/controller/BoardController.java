package com.ishan.syncCanvas.board.controller;

import com.ishan.syncCanvas.board.dto.BoardResponse;
import com.ishan.syncCanvas.board.dto.CreateBoardRequest;
import com.ishan.syncCanvas.board.dto.UpdateBoardRequest;
import com.ishan.syncCanvas.board.service.BoardService;
import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.service.CanvasObjectService;
import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

        private final BoardService boardService;
        private final CanvasObjectService canvasObjectService;

        @PostMapping
        public ResponseEntity<ApiResponse<BoardResponse>> createBoard(
                        @AuthenticationPrincipal UserPrincipal userPrincipal,
                        @Valid @RequestBody CreateBoardRequest request) {

                BoardResponse response = boardService.createBoard(userPrincipal.getId(), request);

                return ResponseUtil.created(
                                "Board created successfully",
                                response);
        }

        @GetMapping
        public ResponseEntity<ApiResponse<Page<BoardResponse>>> getBoards(
                        @AuthenticationPrincipal UserPrincipal userPrincipal,
                        @RequestParam(name = "name", required = false) String name,
                        Pageable pageable) {

                return ResponseUtil.success(
                                boardService.getBoards(userPrincipal.getId(), name, pageable),
                                "Boards fetched successfully",
                                HttpStatus.OK);
        }

        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<BoardResponse>> getBoard(
                        @AuthenticationPrincipal UserPrincipal userPrincipal,
                        @PathVariable UUID id) {

                BoardResponse response = boardService.getBoardById(userPrincipal.getId(), id);

                return ResponseUtil.success(
                                response,
                                "Board fetched successfully",
                                HttpStatus.OK);
        }

        @GetMapping("/{id}/objects")
        public ResponseEntity<ApiResponse<List<CanvasObjectResponse>>> getBoardObjects(
                        @AuthenticationPrincipal UserPrincipal userPrincipal,
                        @PathVariable UUID id) {
                // Ensure the user actually has access to this board before returning its
                // objects
                boardService.getBoardById(userPrincipal.getId(), id);

                List<CanvasObjectResponse> response = canvasObjectService.getObjectsByBoard(id);

                return ResponseUtil.success(
                                response,
                                "Canvas objects fetched successfully",
                                HttpStatus.OK);
        }

        @PatchMapping("/{id}")
        public ResponseEntity<ApiResponse<BoardResponse>> updateBoard(
                        @AuthenticationPrincipal UserPrincipal userPrincipal,
                        @PathVariable UUID id,
                        @Valid @RequestBody UpdateBoardRequest request) {

                return ResponseUtil.success(
                                boardService.updateBoard(userPrincipal.getId(), id, request),
                                "Board updated successfully",
                                HttpStatus.OK);
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<ApiResponse<Void>> deleteBoard(
                        @AuthenticationPrincipal UserPrincipal userPrincipal,
                        @PathVariable UUID id) {

                boardService.deleteBoard(userPrincipal.getId(), id);
                return ResponseUtil.success("Board deleted successfully");
        }
}