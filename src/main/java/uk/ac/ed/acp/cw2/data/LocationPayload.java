package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationPayload(@NotNull positionRegion position, @NotNull Region region) {}
