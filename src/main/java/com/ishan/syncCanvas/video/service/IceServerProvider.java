package com.ishan.syncCanvas.video.service;

import com.ishan.syncCanvas.video.dto.IceServerConfig;

import java.util.List;

/**
 * Source of the STUN/TURN servers handed to browsers for {@code RTCPeerConnection}
 * construction. {@link StaticIceServerProvider} is the only implementation today
 * (fixed configuration/environment variables); the seam exists so a future provider —
 * e.g. one minting short-lived TURN credentials per request — can replace it without
 * touching any caller.
 */
public interface IceServerProvider {

    List<IceServerConfig> getIceServers();
}
