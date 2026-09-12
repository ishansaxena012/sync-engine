package com.ishan.syncCanvas.video;

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

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
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

    @BeforeEach
    void setUp() {
        rateLimiter = new VideoSignalRateLimiter(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void firstMessageInAWindowStartsTheOneSecondExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiter.assertWithinLimit(boardId, userId);

        verify(redisTemplate).expire("video:board:" + boardId + ":ratelimit:" + userId, Duration.ofSeconds(1));
    }

    @Test
    void messagesWithinTheLimitAreAllowedWithoutResettingTheExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        assertThatCode(() -> rateLimiter.assertWithinLimit(boardId, userId)).doesNotThrowAnyException();

        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exceedingTheLimitIsRejected() {
        when(valueOperations.increment(anyString())).thenReturn(31L);

        assertThatThrownBy(() -> rateLimiter.assertWithinLimit(boardId, userId))
                .isInstanceOf(VideoSignalRejectedException.class);
    }

    @Test
    void redisFailureFailsOpenRatherThanBlockingSignaling() {
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> rateLimiter.assertWithinLimit(boardId, userId)).doesNotThrowAnyException();
    }
}
