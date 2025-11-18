package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryAttributes(
        @NotNull
        String attribute,
        @NotNull
        String operator,
        @NotNull
        String value
) {}
