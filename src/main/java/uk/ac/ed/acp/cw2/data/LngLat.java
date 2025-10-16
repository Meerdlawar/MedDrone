package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LngLat(
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180", message = "Longitude must be >= -180")
        @DecimalMax(value = "180",  message = "Longitude must be <= 180")
        Double lng,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90",  message = "Latitude must be >= -90")
        @DecimalMax(value = "90",   message = "Latitude must be <= 90")
        Double lat
) {}