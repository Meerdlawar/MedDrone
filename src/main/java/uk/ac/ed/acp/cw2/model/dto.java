package uk.ac.ed.acp.cw2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class dto {
    public record PairRequest(LngLat position1, LngLat position2) {}
    public record DistanceResponse(double distance) {}
    public record IsCloseResponse(boolean close) {}


    public record StepByAngleRequest(
            @NotNull LngLat start,
            @NotNull @JsonProperty("angle") Double angle   // matches your Postman payload
    ) {
        @JsonCreator public StepByAngleRequest(
                @JsonProperty("start") LngLat start,
                @JsonProperty("angle") Double angle
        ) {
            this.start = start;
            this.angle = angle;
        }
    }

}
