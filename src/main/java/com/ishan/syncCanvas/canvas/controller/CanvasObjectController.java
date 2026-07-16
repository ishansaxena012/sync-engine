package com.ishan.syncCanvas.canvas.controller;

import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.service.CanvasObjectService;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/canvas-objects")
@RequiredArgsConstructor
public class CanvasObjectController {

    private final CanvasObjectService canvasObjectService;

    @PostMapping
    public ResponseEntity<ApiResponse<CanvasObjectResponse>> createObject(@Valid @RequestBody CreateCanvasObjectRequest request) {
        CanvasObjectResponse response = canvasObjectService.createObject(request);
        return ResponseUtil.success(
                response,
                "Canvas object created successfully",
                HttpStatus.CREATED);
    }

    @GetMapping("/board/{boardId}")
    public ResponseEntity<ApiResponse<List<CanvasObjectResponse>>> getObjectsByBoard(@PathVariable UUID boardId) {
        List<CanvasObjectResponse> response = canvasObjectService.getObjectsByBoard(boardId);
        return ResponseUtil.success(
                response,
                "Canvas objects retrieved successfully",
                HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteObject(@PathVariable UUID id) {
        canvasObjectService.deleteObject(id);
        return ResponseUtil.success(
                "Canvas object deleted successfully");
    }
}
