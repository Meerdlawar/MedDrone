package uk.ac.ed.acp.cw2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

public class Dto {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PairRequest(LngLat position1, LngLat position2) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepByAngleRequest(
            @NotNull LngLat start,
            @NotNull @JsonProperty("angle") Double angle
    ) {
        @JsonCreator public StepByAngleRequest(
                @JsonProperty("start") LngLat start,
                @JsonProperty("angle") Double angle
        ) {
            this.start = start;
            this.angle = angle;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record positionRegion(@NotNull Double lng, @NotNull Double lat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Region(
            @NotNull String name,
            @NotNull @Size(min = 4) java.util.List<positionRegion> vertices
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationPayload(@NotNull positionRegion position, @NotNull Region region) {}


}
