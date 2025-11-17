package uk.ac.ed.acp.cw2.data;

import java.util.List;

public record ListDrones(
        int id,
        List<DroneAvailability> availability
) {}