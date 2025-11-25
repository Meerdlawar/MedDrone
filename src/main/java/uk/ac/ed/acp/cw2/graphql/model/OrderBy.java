package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class OrderBy {
    private String field;
    private String direction;
}