package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LngLat(
        @NotNull(message = "Longitude is required")
        Double lng,

        @NotNull(message = "Latitude is required")
        Double lat
) {}