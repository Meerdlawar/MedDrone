package uk.ac.ed.acp.cw2.data;

import java.util.List;

public record DronePath(
        int droneId,
        List<DeliveryPath> deliveries
) {}
