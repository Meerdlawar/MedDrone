package uk.ac.ed.acp.cw2.graphql.model;

import lombok.Data;

@Data
public class CostFilterInput {
    private Double maxCostPerMove;
    private Double maxCostInitial;
    private Double maxCostFinal;
}