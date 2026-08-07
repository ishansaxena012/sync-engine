package com.ishan.syncCanvas.security.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
public class GoogleAuthService {

    private final UserService userService;
    private final String clientId;

    public GoogleAuthService(
            UserService userService,
            @Value("${app.google.client-id}") String clientId) {
        this.userService = userService;
        this.clientId = clientId;
    }

    public User authenticateGoogleToken(String idTokenString) {
        // Dev fallback for testing without Google Cloud Console setup
        if (idTokenString != null && idTokenString.startsWith("dev-token:")) {
            String[] parts = idTokenString.split(":");
            String email = parts.length > 1 ? parts[1] : "dev@example.com";
            String name = parts.length > 2 ? parts[2] : "Dev User";
            String googleId = "google-" + email;
            return userService.findOrCreateGoogleUser(googleId, email, name, "https://lh3.googleusercontent.com/a/default-user");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(clientId.equals("mock-dev-google-client-id") ? null : Collections.singletonList(clientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

                String userId = payload.getSubject();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                return userService.findOrCreateGoogleUser(userId, email, name, pictureUrl);
            } else {
                throw new IllegalArgumentException("Invalid Google ID Token");
            }
        } catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new IllegalArgumentException("Google token verification failed: " + e.getMessage());
        }
    }
}
