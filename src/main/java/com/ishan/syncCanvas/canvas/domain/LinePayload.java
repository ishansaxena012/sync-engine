package com.ishan.syncCanvas.canvas.domain;

public record LinePayload(

        double x2,

        double y2,

        String strokeColor,

        double strokeWidth

) implements CanvasPayload {
}