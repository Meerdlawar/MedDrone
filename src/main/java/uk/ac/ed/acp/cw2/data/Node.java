package uk.ac.ed.acp.cw2.data;

import uk.ac.ed.acp.cw2.dto.LngLat;
import uk.ac.ed.acp.cw2.services.GeometryService;

public class Node {

    private final LngLat xy;
    private double gCost;   // cost from start
    private final double hCost;   // heuristic to goal
    private Node parent;

    public Node(LngLat xy, Node parent, LngLat goal) {
        this.xy = xy;
        this.parent = parent;

        // g: cost from start to this node
        if (parent == null) {
            // start node
            this.gCost = 0;
        } else {
            // cost so far + cost of this step
            this.gCost = parent.gCost + GeometryService.distance(parent.xy, xy);
            // or parent.gCost + STEP_SIZE if each move is constant cost
        }

        // h: straight-line distance to the goal (your heuristic)
        this.hCost = GeometryService.distance(xy, goal);
    }

    public LngLat getXy() {
        return xy;
    }

    public double getGCost() {
        return gCost;
    }

    public double getHCost() {
        return hCost;
    }

    public Node getParent() {
        return parent;
    }

    public double getFCost() {
        return gCost + hCost;
    }

    public void setGCost(double gCost) {
        this.gCost = gCost;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

}