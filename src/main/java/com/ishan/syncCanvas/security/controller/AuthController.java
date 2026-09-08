package com.ishan.syncCanvas.security.controller;

import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.security.dto.GoogleAuthRequest;
import com.ishan.syncCanvas.security.dto.RefreshTokenRequest;
import com.ishan.syncCanvas.security.dto.TokenResponse;
import com.ishan.syncCanvas.security.jwt.JwtTokenProvider;
import com.ishan.syncCanvas.security.jwt.RefreshTokenRevocationService;
import com.ishan.syncCanvas.security.service.GoogleAuthService;
import com.ishan.syncCanvas.user.dto.UserProfileResponse;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.user.mapper.UserMapper;
import com.ishan.syncCanvas.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAuthService googleAuthService;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final UserMapper userMapper;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<TokenResponse>> authenticateGoogle(
            @Valid @RequestBody GoogleAuthRequest request) {

        User user = googleAuthService.authenticateGoogleToken(request.idToken());
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        UserProfileResponse userProfile = userMapper.toResponse(user);
        TokenResponse tokenResponse = new TokenResponse(accessToken, refreshToken, userProfile);

        return ResponseUtil.success(tokenResponse, "Authentication successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        String refreshToken = request.refreshToken();

        if (!tokenProvider.validateToken(refreshToken) || !"REFRESH".equals(tokenProvider.getTokenType(refreshToken))) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String jti = tokenProvider.getTokenId(refreshToken);
        if (refreshTokenRevocationService.isRevoked(jti)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        UUID userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userService.getUserById(userId);

        // Rotate: this refresh token is single-use. Revoking it immediately means a
        // stolen-and-replayed refresh token stops working the moment the legitimate
        // client refreshes, instead of staying valid for its full 7-day lifetime.
        refreshTokenRevocationService.revoke(jti, tokenProvider.getExpiration(refreshToken));

        String newAccessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId());

        UserProfileResponse userProfile = userMapper.toResponse(user);
        TokenResponse tokenResponse = new TokenResponse(newAccessToken, newRefreshToken, userProfile);

        return ResponseUtil.success(tokenResponse, "Token refreshed successfully");
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request) {

        if (request != null && request.refreshToken() != null
                && tokenProvider.validateToken(request.refreshToken())
                && "REFRESH".equals(tokenProvider.getTokenType(request.refreshToken()))) {

            String jti = tokenProvider.getTokenId(request.refreshToken());
            refreshTokenRevocationService.revoke(jti, tokenProvider.getExpiration(request.refreshToken()));
        }

        return ResponseUtil.success("Logged out successfully");
    }
}
