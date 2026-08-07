package com.ishan.syncCanvas.user.service.impl;

import com.ishan.syncCanvas.user.dto.UserProfileResponse;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.user.mapper.UserMapper;
import com.ishan.syncCanvas.user.repository.UserRepository;
import com.ishan.syncCanvas.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public User findOrCreateGoogleUser(String googleId, String email, String name, String profilePicture) {
        return userRepository.findByEmail(email)
                .map(existingUser -> {
                    boolean updated = false;
                    if (existingUser.getGoogleId() == null && googleId != null) {
                        existingUser.setGoogleId(googleId);
                        updated = true;
                    }
                    if (name != null && !name.equals(existingUser.getName())) {
                        existingUser.setName(name);
                        updated = true;
                    }
                    if (profilePicture != null && !profilePicture.equals(existingUser.getProfilePicture())) {
                        existingUser.setProfilePicture(profilePicture);
                        updated = true;
                    }
                    return updated ? userRepository.save(existingUser) : existingUser;
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .googleId(googleId)
                                .email(email)
                                .name(name != null ? name : "User")
                                .profilePicture(profilePicture)
                                .build()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID userId) {
        User user = getUserById(userId);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }
}
