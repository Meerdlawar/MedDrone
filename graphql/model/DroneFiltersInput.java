package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class DroneFiltersInput {
    private AvailabilityFilterInput availability;
    private CapabilityFilterInput capability;
    private CostFilterInput cost;
}