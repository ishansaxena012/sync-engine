package com.ishan.syncCanvas.user.controller;

import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.dto.UserProfileResponse;
import com.ishan.syncCanvas.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // <-- registers bean
@RequestMapping("/api/v1/users") // optional base path
@RequiredArgsConstructor
@Slf4j // optional logging
public class UserController {

    private final UserService userService;

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        if (userPrincipal == null) {
            // Defensive – should never happen with proper security config
            return ResponseUtil.unauthorized("Missing authentication");
        }

        UserProfileResponse response = userService.getUserProfile(userPrincipal.getId());
        return ResponseUtil.success(response, "User profile retrieved successfully");
    }
}
