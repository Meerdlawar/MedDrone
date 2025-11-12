package uk.ac.ed.acp.cw2.data;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QueryAttributes(
        @NotNull
        String attribute,
        @NotNull
        String operator,
        @NotNull
        String value
) {}
