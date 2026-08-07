package com.ishan.syncCanvas.canvas.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImagePayload(

        String url,

        double width,

        double height

) implements CanvasPayload {
}