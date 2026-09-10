package com.ishan.syncCanvas.websocket.security;

import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.jwt.JwtTokenProvider;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requires a valid access JWT on every STOMP CONNECT frame.
 *
 * <p>{@code /ws/**} is {@code permitAll()} at the Spring Security/HTTP layer because
 * that only covers the initial SockJS handshake — the actual collaboration protocol
 * runs over STOMP frames inside that connection, which Spring Security's HTTP filter
 * chain never sees. Without this interceptor, any anonymous client could open a STOMP
 * session and read/write every board's live operations.
 *
 * <p>The resolved {@link UserPrincipal} is attached to the STOMP session so downstream
 * {@code @MessageMapping} handlers can recover the authenticated user via the standard
 * {@link java.security.Principal} argument.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    // Matches both /topic/boards/{boardId}/cursor and /topic/boards/{boardId}/presence —
    // both are ephemeral, board-scoped broadcast topics with the same privacy
    // requirement as sending to them: an authenticated user with no access to the board
    // must not be able to passively subscribe and watch either stream either.
    private static final Pattern BOARD_SCOPED_TOPIC = Pattern.compile("^/topic/boards/([^/]+)/(?:cursor|presence)$");

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final BoardAccessGuard boardAccessGuard;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        // MessageHeaderAccessor.getAccessor (not StompHeaderAccessor.wrap) is required
        // here: the STOMP protocol handler attaches a mutable accessor to the CONNECT
        // message, and getAccessor retrieves that same live instance. wrap() instead
        // creates a detached copy — setUser() on it would silently fail to attach the
        // principal to the session, leaving every downstream @MessageMapping's
        // Principal argument null.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);

            if (!StringUtils.hasText(token)
                    || !tokenProvider.validateToken(token)
                    || !"ACCESS".equals(tokenProvider.getTokenType(token))) {
                log.warn("Rejected STOMP CONNECT with missing or invalid token");
                throw new MessagingException("Missing or invalid authentication token");
            }

            UUID userId = tokenProvider.getUserIdFromToken(token);
            User user = userService.getUserById(userId);
            accessor.setUser(UserPrincipal.create(user));

        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            UUID scopedBoardId = extractBoardScopedTopicId(accessor.getDestination());
            if (scopedBoardId != null) {
                Principal user = accessor.getUser();
                if (user == null) {
                    throw new MessagingException("Unauthenticated subscription rejected");
                }
                boardAccessGuard.assertAccessible(scopedBoardId, UUID.fromString(user.getName()));
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return accessor.getFirstNativeHeader("token");
    }

    private static UUID extractBoardScopedTopicId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = BOARD_SCOPED_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
