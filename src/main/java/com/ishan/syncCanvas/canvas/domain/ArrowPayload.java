package com.ishan.syncCanvas.canvas.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArrowPayload(

        double[] points,

        String stroke,

        double strokeWidth,

        boolean pointerAtStart,

        boolean pointerAtEnd

) implements CanvasPayload {
}
