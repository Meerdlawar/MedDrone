package uk.ac.ed.acp.cw2.data;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Region(
            @NotNull String name,
            @NotNull @Size(min = 4) java.util.List<positionRegion> vertices
    ) {}