package com.ishan.syncCanvas.canvas.dto;

import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.domain.CanvasPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCanvasObjectRequest {

    @NotNull(message = "Board ID is required")
    private UUID boardId;

    @NotNull(message = "Type is required")
    private CanvasObjectType type;

    private double x;
    private double y;
    private double rotation;
    private int zindex;

    @NotNull(message = "Payload is required")
    @Valid
    private CanvasPayload payload;

    private UUID createdBy;
}
