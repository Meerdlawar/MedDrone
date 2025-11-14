package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

@JsonIgnoreProperties(ignoreUnknown = true)
    public record PosOnePosTwo(@Valid @NotNull LngLat position1, @Valid @NotNull LngLat position2) {}