package com.ishan.syncCanvas.canvas.controller;

import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.service.CanvasObjectService;
import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/canvas-objects")
@RequiredArgsConstructor
public class CanvasObjectController {

    private final CanvasObjectService canvasObjectService;

    @PostMapping
    public ResponseEntity<ApiResponse<CanvasObjectResponse>> createObject(
            @Valid @RequestBody CreateCanvasObjectRequest request) {

        CanvasObjectResponse response = canvasObjectService.createObject(request);

        return ResponseUtil.success(
                response,
                "Canvas object created successfully",
                HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteObject(
            @PathVariable UUID id) {

        canvasObjectService.deleteObject(id);

        return ResponseUtil.success(
                "Canvas object deleted successfully");
    }
}