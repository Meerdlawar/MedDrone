package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class DroneFilters {
    private AvailabilityFilter availability;
    private CapabilityFilter capability;
    private CostFilter cost;
}