package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LngLat(
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-4", message = "Longitude must be within Edinburgh")
        @DecimalMax(value = "-2",  message = "Longitude must be within Edinburgh")
        Double lng,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "56",  message = "Latitude must be within Edinburgh")
        @DecimalMax(value = "57",   message = "Latitude must be witin Edinburgh")
        Double lat
) {}