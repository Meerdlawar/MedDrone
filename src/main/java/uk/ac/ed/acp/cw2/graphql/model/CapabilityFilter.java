package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class CapabilityFilter {
    private Double minCapacity;
    private Double maxCapacity;
    private Boolean cooling;
    private Boolean heating;
}