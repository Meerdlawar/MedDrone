package uk.ac.ed.acp.cw2.data;

import java.util.List;

public record DeliveryPlan(
        double totalCost,
        int totalMoves,
        List<DronePath> dronePaths
) {}
