package uk.ac.ed.acp.cw2.dto;

import java.util.List;

public record ListDrones(
        int id,
        List<DroneAvailability> availability
) {}