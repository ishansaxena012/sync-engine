package com.ishan.syncCanvas.video.service;

import com.ishan.syncCanvas.video.dto.VideoSignalType;
import com.ishan.syncCanvas.video.exception.VideoSignalRejectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Smallest workable abuse guard for signaling: a per-(board, user, message-class) fixed
 * one-second window, counted with {@code INCR} and expired with {@code EXPIRE} on the
 * window's first message — the same atomic-counter idiom {@code VideoRoomService}
 * already uses, so it is correct across every ECS instance a user's messages might land
 * on rather than only within one JVM. Not a token bucket or a sliding log; if
 * finer-grained limiting is ever needed later, this is the one place to extend.
 *
 * <p>ICE candidates and SDP offers/answers are counted in separate buckets with
 * separate, centrally configured limits ({@code video.signaling.*}): a real negotiation
 * can burst a dozen ICE candidates in a second, while more than a handful of
 * offers/answers per second from one participant is never legitimate. Keeping the
 * buckets separate means a legitimate ICE burst never eats into the much stricter SDP
 * budget.
 *
 * <p>Fails open: a Redis error here is logged and treated as "allow" rather than
 * blocking signaling outright, since this guard is an abuse safeguard, not the source of
 * truth for room membership the way {@code VideoRoomService}'s own keys are.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSignalRateLimiter {

    @Value("${video.signaling.ice-candidates-per-second:20}")
    private int iceCandidatesPerSecond;

    @Value("${video.signaling.sdp-messages-per-second:5}")
    private int sdpMessagesPerSecond;

    private final StringRedisTemplate redisTemplate;

    public void assertWithinLimit(UUID boardId, UUID userId, VideoSignalType type) {
        boolean isIce = type == VideoSignalType.ICE_CANDIDATE;
        int limit = isIce ? iceCandidatesPerSecond : sdpMessagesPerSecond;
        String bucket = isIce ? "ice" : "sdp";
        String key = "video:board:" + boardId + ":ratelimit:" + bucket + ":" + userId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(1));
            }
            if (count != null && count > limit) {
                throw new VideoSignalRejectedException("Too many signaling messages — please slow down");
            }
        } catch (VideoSignalRejectedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Signal rate-limit check failed for board {} user {} type {}; allowing message", boardId, userId, type, ex);
        }
    }
}
