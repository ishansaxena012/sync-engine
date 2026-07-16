package com.ishan.syncCanvas.canvas.domain;

public record TextPayload(

        String text,

        String fontFamily,

        int fontSize,

        boolean bold,

        boolean italic,

        String color

) implements CanvasPayload {
}