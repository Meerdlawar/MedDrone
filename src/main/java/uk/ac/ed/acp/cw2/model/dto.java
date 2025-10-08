package uk.ac.ed.acp.cw2.model;

public class dto {
    public record PairRequest(LngLat position1, LngLat position2) {}
    public record DistanceResponse(double distance) {}
    public record IsCloseResponse(boolean close) {}
}
