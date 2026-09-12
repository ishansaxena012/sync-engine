package com.ishan.syncCanvas.video.service;

import com.ishan.syncCanvas.video.dto.IceServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the ICE server list from plain {@code video.webrtc.*} configuration —
 * environment variables in every real deployment, never hard-coded. TURN is entirely
 * optional: with no {@code TURN_URL} configured, only the STUN entry is returned (a
 * public STUN server is not a secret, so it is safe to ship a default).
 *
 * <p>{@link #toString()} is overridden defensively so an accidental {@code
 * log.info(provider)} anywhere can never leak the TURN credential — the same guarantee
 * Spring Boot Actuator's own {@code /configprops} sanitization gives any property whose
 * name contains "credential".
 */
@Component
public class StaticIceServerProvider implements IceServerProvider {

    @Value("${video.webrtc.stun-urls:stun:stun.l.google.com:19302}")
    private String stunUrls;

    @Value("${video.webrtc.turn-url:}")
    private String turnUrl;

    @Value("${video.webrtc.turn-username:}")
    private String turnUsername;

    @Value("${video.webrtc.turn-credential:}")
    private String turnCredential;

    @Override
    public List<IceServerConfig> getIceServers() {
        List<IceServerConfig> servers = new ArrayList<>();

        List<String> stun = splitUrls(stunUrls);
        if (!stun.isEmpty()) {
            servers.add(new IceServerConfig(stun, null, null));
        }

        if (StringUtils.hasText(turnUrl)) {
            servers.add(new IceServerConfig(splitUrls(turnUrl), turnUsername, turnCredential));
        }

        return servers;
    }

    private static List<String> splitUrls(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Override
    public String toString() {
        return "StaticIceServerProvider{turnConfigured=" + StringUtils.hasText(turnUrl) + "}";
    }
}
