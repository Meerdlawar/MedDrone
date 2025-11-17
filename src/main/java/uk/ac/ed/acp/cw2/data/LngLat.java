package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LngLat(
        @NotNull(message = "Longitude is required")
        Double lng,

        @NotNull(message = "Latitude is required")
        Double lat
) {}