package uk.ac.ed.acp.cw2.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Node {

    private final int x;
    private final int y;

    private int gCost;      // cost from start to this node
    private int hCost;      // heuristic cost from this node to goal
    private Node parent;    // for path reconstruction

    public Node(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getFCost() {
        return gCost + hCost;
    }
}