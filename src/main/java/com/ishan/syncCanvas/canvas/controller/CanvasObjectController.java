package com.ishan.syncCanvas.canvas.controller;

import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.service.CanvasObjectService;
import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/canvas-objects")
@RequiredArgsConstructor
public class CanvasObjectController {

    private final CanvasObjectService canvasObjectService;

    @PostMapping
    public ResponseEntity<ApiResponse<CanvasObjectResponse>> createObject(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateCanvasObjectRequest request) {

        CanvasObjectResponse response = canvasObjectService.createObject(userPrincipal.getId(), request);

        return ResponseUtil.success(
                response,
                "Canvas object created successfully",
                HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteObject(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id) {

        canvasObjectService.deleteObject(userPrincipal.getId(), id);

        return ResponseUtil.success(
                "Canvas object deleted successfully");
    }
}