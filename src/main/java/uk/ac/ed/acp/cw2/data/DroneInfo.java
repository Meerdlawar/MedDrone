package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DroneInfo (
        @NotNull
        String name,
        @NotNull
        int id,
        @NotNull
        DroneCapability capability
) {}
