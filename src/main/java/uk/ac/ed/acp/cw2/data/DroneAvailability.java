package uk.ac.ed.acp.cw2.data;

import java.time.LocalTime;

public record DroneAvailability(
        String dayOfWeek,
        LocalTime from,
        LocalTime until
) {}