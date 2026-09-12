package com.ishan.syncCanvas.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.video.dto.IceServerConfig;
import com.ishan.syncCanvas.video.service.StaticIceServerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration-surface tests for the ICE server list: STUN only, STUN + TURN, no TURN
 * configured, and multiple STUN URLs -- none of these require a real production TURN
 * server or credential, only plain string configuration values.
 */
class StaticIceServerProviderTest {

    private StaticIceServerProvider provider(String stunUrls, String turnUrl, String turnUsername, String turnCredential) {
        StaticIceServerProvider provider = new StaticIceServerProvider();
        ReflectionTestUtils.setField(provider, "stunUrls", stunUrls);
        ReflectionTestUtils.setField(provider, "turnUrl", turnUrl);
        ReflectionTestUtils.setField(provider, "turnUsername", turnUsername);
        ReflectionTestUtils.setField(provider, "turnCredential", turnCredential);
        return provider;
    }

    @Test
    void stunOnlyConfigurationReturnsExactlyOneEntryWithNoCredentials() {
        StaticIceServerProvider provider = provider("stun:stun.l.google.com:19302", "", "", "");

        List<IceServerConfig> servers = provider.getIceServers();

        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).urls()).containsExactly("stun:stun.l.google.com:19302");
        assertThat(servers.get(0).username()).isNull();
        assertThat(servers.get(0).credential()).isNull();
    }

    @Test
    void stunPlusTurnConfigurationReturnsBothEntries() {
        StaticIceServerProvider provider = provider(
                "stun:stun.l.google.com:19302", "turn:turn.example.com:3478", "alice", "s3cret");

        List<IceServerConfig> servers = provider.getIceServers();

        assertThat(servers).hasSize(2);
        IceServerConfig turnEntry = servers.stream().filter(s -> s.username() != null).findFirst().orElseThrow();
        assertThat(turnEntry.urls()).containsExactly("turn:turn.example.com:3478");
        assertThat(turnEntry.username()).isEqualTo("alice");
        assertThat(turnEntry.credential()).isEqualTo("s3cret");
    }

    @Test
    void noTurnConfiguredOmitsTheTurnEntryEntirelyRatherThanSendingBlankCredentials() {
        StaticIceServerProvider provider = provider("stun:stun.l.google.com:19302", "", "", "");

        List<IceServerConfig> servers = provider.getIceServers();

        assertThat(servers).noneMatch(s -> s.username() != null);
    }

    @Test
    void multipleCommaSeparatedStunUrlsBecomeOneEntryWithSeveralAlternates() {
        StaticIceServerProvider provider = provider(
                "stun:a.example.com:19302, stun:b.example.com:19302", "", "", "");

        List<IceServerConfig> servers = provider.getIceServers();

        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).urls()).containsExactly("stun:a.example.com:19302", "stun:b.example.com:19302");
    }

    @Test
    void blankStunConfigurationReturnsNoStunEntry() {
        StaticIceServerProvider provider = provider("  ", "", "", "");

        assertThat(provider.getIceServers()).isEmpty();
    }

    @Test
    void turnCredentialNeverAppearsInTheProvidersOwnToString() {
        // Defensive: an accidental log.info(provider) anywhere must never leak this.
        StaticIceServerProvider provider = provider(
                "stun:stun.l.google.com:19302", "turn:turn.example.com:3478", "alice", "s3cret-value");

        assertThat(provider.toString()).doesNotContain("s3cret-value").doesNotContain("alice");
    }

    @Test
    void stunOnlyEntrySerializesWithoutUsernameOrCredentialKeysAtAll() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        StaticIceServerProvider provider = provider("stun:stun.l.google.com:19302", "", "", "");

        String json = objectMapper.writeValueAsString(provider.getIceServers().get(0));

        assertThat(json).doesNotContain("username").doesNotContain("credential");
    }
}
