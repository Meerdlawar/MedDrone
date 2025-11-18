package uk.ac.ed.acp.cw2.dto;

import java.time.LocalTime;

public record DroneAvailability(
        String dayOfWeek,
        LocalTime from,
        LocalTime until
) {}