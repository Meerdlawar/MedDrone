package uk.ac.ed.acp.cw2.data;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;

@JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationPayload(@Valid @NotNull PositionRegion position, @Valid @NotNull Region region) {}
