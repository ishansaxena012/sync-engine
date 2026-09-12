package com.ishan.syncCanvas.video;

import com.ishan.syncCanvas.video.actuator.VideoConfigEndpoint;
import com.ishan.syncCanvas.video.dto.IceServerConfig;
import com.ishan.syncCanvas.video.service.IceServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * This endpoint exists purely to report booleans/limits for internal diagnostics; it is
 * not added to {@code management.endpoints.web.exposure.include} in application.yml, so
 * it is never reachable over HTTP by default. These tests pin down the one thing that
 * actually matters if that ever changes: the payload can never contain a URL, username,
 * or credential, only derived booleans and numeric limits.
 */
@ExtendWith(MockitoExtension.class)
class VideoConfigEndpointTest {

    @Mock
    private IceServerProvider iceServerProvider;

    private VideoConfigEndpoint endpoint() {
        VideoConfigEndpoint endpoint = new VideoConfigEndpoint(iceServerProvider);
        ReflectionTestUtils.setField(endpoint, "maxParticipants", 4);
        ReflectionTestUtils.setField(endpoint, "iceCandidatesPerSecond", 20);
        ReflectionTestUtils.setField(endpoint, "sdpMessagesPerSecond", 5);
        ReflectionTestUtils.setField(endpoint, "maxSdpPayloadBytes", 65536);
        ReflectionTestUtils.setField(endpoint, "maxIcePayloadBytes", 8192);
        return endpoint;
    }

    @Test
    void reportsStunAndTurnConfiguredWhenBothArePresent() {
        when(iceServerProvider.getIceServers()).thenReturn(List.of(
                new IceServerConfig(List.of("stun:stun.l.google.com:19302"), null, null),
                new IceServerConfig(List.of("turn:turn.example.com:3478"), "alice", "s3cret")));

        Map<String, Object> config = endpoint().videoConfig();

        assertThat(config).containsEntry("stunConfigured", true).containsEntry("turnConfigured", true);
    }

    @Test
    void reportsTurnNotConfiguredWhenOnlyStunIsPresent() {
        when(iceServerProvider.getIceServers()).thenReturn(
                List.of(new IceServerConfig(List.of("stun:stun.l.google.com:19302"), null, null)));

        Map<String, Object> config = endpoint().videoConfig();

        assertThat(config).containsEntry("stunConfigured", true).containsEntry("turnConfigured", false);
    }

    @Test
    void reportsLimitsButNeverAnyUrlUsernameOrCredential() {
        when(iceServerProvider.getIceServers()).thenReturn(List.of(
                new IceServerConfig(List.of("turn:turn.example.com:3478"), "alice", "top-secret-credential")));

        Map<String, Object> config = endpoint().videoConfig();

        assertThat(config).containsEntry("maxParticipants", 4)
                .containsEntry("iceCandidatesPerSecondLimit", 20)
                .containsEntry("sdpMessagesPerSecondLimit", 5)
                .containsEntry("maxSdpPayloadBytes", 65536)
                .containsEntry("maxIcePayloadBytes", 8192);
        assertThat(config.toString()).doesNotContain("top-secret-credential").doesNotContain("turn:turn.example.com");
    }
}
