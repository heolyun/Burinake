package com.burinake.dto;

public record BoundingBox(
        double x,
        double y,
        double width,
        double height,
        String label,
        Double score
) {
}
