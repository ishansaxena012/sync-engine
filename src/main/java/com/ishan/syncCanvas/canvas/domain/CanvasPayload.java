package com.ishan.syncCanvas.canvas.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RectanglePayload.class, name = "RECTANGLE"),
        @JsonSubTypes.Type(value = TextPayload.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ImagePayload.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = LinePayload.class, name = "LINE")
})
public sealed interface CanvasPayload
        permits RectanglePayload,
        TextPayload,
        ImagePayload,
        LinePayload {
}