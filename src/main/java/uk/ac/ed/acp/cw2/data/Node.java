package uk.ac.ed.acp.cw2.data;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ed.acp.cw2.dto.LngLat;

// Every node has:
//      - G cost
//      - H cost
//      - F cost
//      - Neighbors
//      - Step size of drone is 0.00015 so node area is 0.00015^2

@Getter
@Setter
public class Node {

    LngLat xy;

    private double gCost;      // cost from start to this node
    private double hCost;      // heuristic cost from this node to goal
    private Node parent;    // for path reconstruction

    public Node(LngLat xy) {
        this.xy = xy;
    }

    public double getFCost() {
        return gCost + hCost;
    }
}