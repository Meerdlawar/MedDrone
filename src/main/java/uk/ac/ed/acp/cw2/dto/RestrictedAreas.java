package uk.ac.ed.acp.cw2.dto;

import java.util.List;

public record RestrictedAreas(
        String name,
        int id,
        Limits limits,
        List<LngLat> vertices
) {}
