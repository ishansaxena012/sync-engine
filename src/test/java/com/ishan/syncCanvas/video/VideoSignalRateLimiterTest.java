package com.ishan.syncCanvas.video;

import com.ishan.syncCanvas.video.dto.VideoSignalType;
import com.ishan.syncCanvas.video.exception.VideoSignalRejectedException;
import com.ishan.syncCanvas.video.service.VideoSignalRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoSignalRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private VideoSignalRateLimiter rateLimiter;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private String iceKey;
    private String sdpKey;

    @BeforeEach
    void setUp() {
        rateLimiter = new VideoSignalRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(rateLimiter, "iceCandidatesPerSecond", 20);
        ReflectionTestUtils.setField(rateLimiter, "sdpMessagesPerSecond", 5);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        iceKey = "video:board:" + boardId + ":ratelimit:ice:" + userId;
        sdpKey = "video:board:" + boardId + ":ratelimit:sdp:" + userId;
    }

    @Test
    void firstMessageInAWindowStartsTheOneSecondExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.ICE_CANDIDATE);

        verify(redisTemplate).expire(iceKey, Duration.ofSeconds(1));
    }

    @Test
    void messagesWithinTheLimitAreAllowedWithoutResettingTheExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        assertThatCode(() -> rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.ICE_CANDIDATE))
                .doesNotThrowAnyException();

        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exceedingTheIceLimitIsRejected() {
        when(valueOperations.increment(eq(iceKey))).thenReturn(21L);

        assertThatThrownBy(() -> rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.ICE_CANDIDATE))
                .isInstanceOf(VideoSignalRejectedException.class);
    }

    @Test
    void exceedingTheStricterSdpLimitIsRejected() {
        // The SDP limit (5/sec) is much lower than ICE's (20/sec) -- a count that would
        // be fine for ICE candidates already exceeds it.
        when(valueOperations.increment(eq(sdpKey))).thenReturn(6L);

        assertThatThrownBy(() -> rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.OFFER))
                .isInstanceOf(VideoSignalRejectedException.class);
    }

    @Test
    void iceAndSdpMessagesAreCountedInSeparateBucketsSoOneCannotStarveTheOther() {
        when(valueOperations.increment(eq(iceKey))).thenReturn(15L); // within ICE's 20/sec
        when(valueOperations.increment(eq(sdpKey))).thenReturn(2L);  // within SDP's 5/sec

        assertThatCode(() -> {
            rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.ICE_CANDIDATE);
            rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.OFFER);
        }).doesNotThrowAnyException();

        verify(valueOperations).increment(eq(iceKey));
        verify(valueOperations).increment(eq(sdpKey));
    }

    @Test
    void answerIsCountedInTheSameSdpBucketAsOffer() {
        when(valueOperations.increment(eq(sdpKey))).thenReturn(6L);

        assertThatThrownBy(() -> rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.ANSWER))
                .isInstanceOf(VideoSignalRejectedException.class);
    }

    @Test
    void redisFailureFailsOpenRatherThanBlockingSignaling() {
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> rateLimiter.assertWithinLimit(boardId, userId, VideoSignalType.ICE_CANDIDATE))
                .doesNotThrowAnyException();
    }
}
