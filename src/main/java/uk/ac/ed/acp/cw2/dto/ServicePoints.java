package uk.ac.ed.acp.cw2.dto;

public record ServicePoints(
        String name,
        int id,
        LngLat location
) {}
