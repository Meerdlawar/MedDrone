package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

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