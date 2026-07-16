package com.ishan.syncCanvas.canvas.dto;

import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.domain.CanvasPayload;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasObjectResponse {

    private UUID id;
    private UUID boardId;
    private CanvasObjectType type;
    private double x;
    private double y;
    private double rotation;
    private int zindex;
    private CanvasPayload payload;
    private UUID createdBy;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
