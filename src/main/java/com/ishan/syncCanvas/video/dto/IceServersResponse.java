package com.ishan.syncCanvas.video.dto;

import java.util.List;

/** REST response body for {@code GET /api/v1/boards/{boardId}/video/ice-servers}. */
public record IceServersResponse(List<IceServerConfig> iceServers) {
}
