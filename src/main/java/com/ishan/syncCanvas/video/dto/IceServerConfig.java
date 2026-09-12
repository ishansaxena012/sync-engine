package com.ishan.syncCanvas.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One entry of the {@code RTCConfiguration.iceServers} list handed to the browser's
 * {@code RTCPeerConnection}. {@code username}/{@code credential} are omitted from the
 * JSON entirely (not sent as {@code null}) for a STUN-only entry, matching what a real
 * ICE server descriptor looks like.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IceServerConfig(List<String> urls, String username, String credential) {
}
