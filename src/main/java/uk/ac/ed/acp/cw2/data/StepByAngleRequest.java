package uk.ac.ed.acp.cw2.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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