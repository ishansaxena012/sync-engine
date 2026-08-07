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
        @JsonSubTypes.Type(value = CirclePayload.class, name = "CIRCLE"),
        @JsonSubTypes.Type(value = TextPayload.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ImagePayload.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = LinePayload.class, name = "LINE"),
        @JsonSubTypes.Type(value = ArrowPayload.class, name = "ARROW"),
        @JsonSubTypes.Type(value = EllipsePayload.class, name = "ELLIPSE"),
        @JsonSubTypes.Type(value = TrianglePayload.class, name = "TRIANGLE"),
        @JsonSubTypes.Type(value = DiamondPayload.class, name = "DIAMOND")
})
public sealed interface CanvasPayload permits RectanglePayload,
        CirclePayload,
        TextPayload,
        ImagePayload,
        LinePayload,
        ArrowPayload,
        EllipsePayload,
        TrianglePayload,
        DiamondPayload {
}