package com.burinake.dto;

public record BoundingBox(
        int x,
        int y,
        int width,
        int height,
        String label,
        Double score
) {
}
