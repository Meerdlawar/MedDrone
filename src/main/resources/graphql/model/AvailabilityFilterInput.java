package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class AvailabilityFilterInput {
    private String dayOfWeek;
    private String time;
}