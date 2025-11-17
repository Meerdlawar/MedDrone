package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
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
