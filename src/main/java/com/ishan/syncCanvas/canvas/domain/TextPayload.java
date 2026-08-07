package com.ishan.syncCanvas.canvas.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TextPayload(

        String text,

        int fontSize,

        double width,

        @JsonAlias("fillColor")
        String fill

) implements CanvasPayload {
}