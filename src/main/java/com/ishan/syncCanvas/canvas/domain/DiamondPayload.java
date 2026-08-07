package com.ishan.syncCanvas.canvas.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiamondPayload(

        double width,

        double height,

        @JsonAlias("fillColor")
        String fill,

        String stroke,

        double strokeWidth

) implements CanvasPayload {
}
