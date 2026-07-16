package com.ishan.syncCanvas.canvas.domain;

public record RectanglePayload(

        double width,

        double height,

        double cornerRadius,

        String fillColor,

        String strokeColor

) implements CanvasPayload {
}