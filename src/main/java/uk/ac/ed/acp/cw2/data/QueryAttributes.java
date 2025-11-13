package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryAttributes(
        @NotNull
        String attribute,
        @NotNull
        String operator,
        @NotNull
        String value
) {}
