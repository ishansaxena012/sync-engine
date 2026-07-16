package com.ishan.syncCanvas.canvas.domain;

public record ImagePayload(

        String url,

        double width,

        double height

) implements CanvasPayload {
}