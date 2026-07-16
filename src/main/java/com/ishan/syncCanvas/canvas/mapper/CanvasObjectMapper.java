package com.ishan.syncCanvas.canvas.mapper;

import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CanvasObjectMapper {

    CanvasObject toEntity(CreateCanvasObjectRequest request);

    CanvasObjectResponse toResponse(CanvasObject canvasObject);
}
