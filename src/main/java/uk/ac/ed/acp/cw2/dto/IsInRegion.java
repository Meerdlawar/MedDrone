package uk.ac.ed.acp.cw2.dto;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;

@JsonIgnoreProperties(ignoreUnknown = true)
    public record IsInRegion(
            @Valid @NotNull PositionRegion position,
            @Valid @NotNull Region region)
{}
