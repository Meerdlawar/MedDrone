package uk.ac.ed.acp.cw2.dto;

import java.util.List;

public record DronesForServicePoints(
        int servicePointId,
        List<ListDrones> drones
) {}
