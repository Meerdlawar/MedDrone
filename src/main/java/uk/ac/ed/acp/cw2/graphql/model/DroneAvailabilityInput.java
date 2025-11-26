package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class DroneAvailabilityInput {
    private String dayOfWeek;
    private String from;
    private String until;
}