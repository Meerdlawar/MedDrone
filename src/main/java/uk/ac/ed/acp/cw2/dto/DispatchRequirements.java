package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DispatchRequirements(
        // Required
        @NotNull
        Double capacity,

        // Optional – if missing in JSON, Jackson defaults to false
        boolean cooling,

        // Optional – if missing in JSON, Jackson defaults to false
        boolean heating,

        // Optional – only used if not null
        Double maxCost
) {}
