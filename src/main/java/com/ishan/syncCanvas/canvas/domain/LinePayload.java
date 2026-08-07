package com.ishan.syncCanvas.canvas.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LinePayload(

        double[] points,

        String stroke,

        double strokeWidth

) implements CanvasPayload {
}