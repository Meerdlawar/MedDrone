package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class OrderByInput {
    private String field;
    private String direction;
}