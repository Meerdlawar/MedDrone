package uk.ac.ed.acp.cw2.data;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationPayload(@NotNull PositionRegion position, @NotNull Region region) {}
