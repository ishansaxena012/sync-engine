package com.ishan.syncCanvas.canvas.mapper;

import com.ishan.syncCanvas.canvas.dto.CanvasObjectResponse;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CanvasObjectMapper {

    @org.mapstruct.Mapping(target = "createdAt", ignore = true)
    @org.mapstruct.Mapping(target = "updatedAt", ignore = true)
    @org.mapstruct.Mapping(target = "version", ignore = true)
    CanvasObject toEntity(CreateCanvasObjectRequest request);

    CanvasObjectResponse toResponse(CanvasObject canvasObject);
}
