package uk.ac.ed.acp.cw2.dto;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
    public record Region(
            @NotNull String name,
            @NotNull @Size(min = 4) List<PositionRegion> vertices
    ) {}