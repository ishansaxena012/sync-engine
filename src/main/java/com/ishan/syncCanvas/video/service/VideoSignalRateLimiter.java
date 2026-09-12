package com.ishan.syncCanvas.video.service;

import com.ishan.syncCanvas.video.exception.VideoSignalRejectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Smallest workable abuse guard for signaling: a per-(board, user) fixed one-second
 * window, counted with {@code INCR} and expired with {@code EXPIRE} on the window's
 * first message — the same atomic-counter idiom {@code VideoRoomService} already uses,
 * so it is correct across every ECS instance a user's messages might land on rather than
 * only within one JVM. Not a token bucket or a sliding log; if finer-grained limiting is
 * ever needed later, this is the one place to extend.
 *
 * <p>Fails open: a Redis error here is logged and treated as "allow" rather than
 * blocking signaling outright, since this guard is an abuse safeguard, not the source of
 * truth for room membership the way {@code VideoRoomService}'s own keys are.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSignalRateLimiter {

    private static final int MAX_MESSAGES_PER_SECOND = 30;

    private final StringRedisTemplate redisTemplate;

    public void assertWithinLimit(UUID boardId, UUID userId) {
        String key = "video:board:" + boardId + ":ratelimit:" + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(1));
            }
            if (count != null && count > MAX_MESSAGES_PER_SECOND) {
                throw new VideoSignalRejectedException("Too many signaling messages — please slow down");
            }
        } catch (VideoSignalRejectedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Signal rate-limit check failed for board {} user {}; allowing message", boardId, userId, ex);
        }
    }
}
