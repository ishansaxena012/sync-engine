package com.ishan.syncCanvas.video.dto;

/** WebRTC negotiation frame types relayed opaquely between two call participants. */
public enum VideoSignalType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE
}
