package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class Location {
    private Double lat;
    private Double lng;
    private Double radiusDegrees;
}