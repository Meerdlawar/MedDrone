package uk.ac.ed.acp.cw2.data;

import java.util.List;

public record DronesForServicePoints(
        int servicePointId,
        List<ListDrones> drones
) {}
