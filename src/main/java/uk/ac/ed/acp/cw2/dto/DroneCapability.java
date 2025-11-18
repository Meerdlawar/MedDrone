package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DroneCapability (
        @NotNull
        boolean cooling,
        @NotNull
        boolean heating,
        @NotNull
        double capacity,
        @NotNull
        double maxMoves,
        @NotNull
        double costPerMove,
        @NotNull
        double costInitial,
        @NotNull
        double costFinal
) {}