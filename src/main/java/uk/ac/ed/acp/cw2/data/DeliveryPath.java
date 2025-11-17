package uk.ac.ed.acp.cw2.data;

import java.util.List;

public record DeliveryPath(
        int deliveryId,
        List<LngLat> flightPath
) {}
