package com.ishan.syncCanvas.video.actuator;

import com.ishan.syncCanvas.video.service.IceServerProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Internal diagnostic endpoint reporting whether the video/WebRTC infrastructure is
 * configured, without ever exposing the configuration values themselves — only booleans
 * and limits, never a STUN/TURN URL, username, or credential.
 *
 * <p>Registered but deliberately <strong>not</strong> added to {@code
 * management.endpoints.web.exposure.include} in {@code application.yml} (which lists
 * only {@code health}), so it is not reachable over HTTP by default — the same posture
 * every other actuator endpoint in this project already has. This class exists so the
 * information is available to internal tooling (JMX, or a future deliberate exposure
 * decision) without ever being a public endpoint.
 */
@Component
@Endpoint(id = "videoConfig")
@RequiredArgsConstructor
public class VideoConfigEndpoint {

    private final IceServerProvider iceServerProvider;

    @Value("${video.room.max-participants:4}")
    private int maxParticipants;

    @Value("${video.signaling.ice-candidates-per-second:20}")
    private int iceCandidatesPerSecond;

    @Value("${video.signaling.sdp-messages-per-second:5}")
    private int sdpMessagesPerSecond;

    @Value("${video.signaling.max-sdp-payload-bytes:65536}")
    private int maxSdpPayloadBytes;

    @Value("${video.signaling.max-ice-payload-bytes:8192}")
    private int maxIcePayloadBytes;

    @ReadOperation
    public Map<String, Object> videoConfig() {
        var servers = iceServerProvider.getIceServers();
        boolean stunConfigured = servers.stream().anyMatch(s -> s.username() == null);
        boolean turnConfigured = servers.stream().anyMatch(s -> s.username() != null);

        return Map.of(
                "stunConfigured", stunConfigured,
                "turnConfigured", turnConfigured,
                "maxParticipants", maxParticipants,
                "iceCandidatesPerSecondLimit", iceCandidatesPerSecond,
                "sdpMessagesPerSecondLimit", sdpMessagesPerSecond,
                "maxSdpPayloadBytes", maxSdpPayloadBytes,
                "maxIcePayloadBytes", maxIcePayloadBytes);
    }
}
