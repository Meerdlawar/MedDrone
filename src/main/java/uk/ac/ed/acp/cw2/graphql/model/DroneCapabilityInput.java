package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class DroneCapabilityInput {
    private Boolean cooling;
    private Boolean heating;
    private Double capacity;
    private Double maxMoves;
    private Double costPerMove;
    private Double costInitial;
    private Double costFinal;
}