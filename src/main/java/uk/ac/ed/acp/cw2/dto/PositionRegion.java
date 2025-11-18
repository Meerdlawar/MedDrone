package uk.ac.ed.acp.cw2.dto;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionRegion(@NotNull Double lng, @NotNull Double lat) {}