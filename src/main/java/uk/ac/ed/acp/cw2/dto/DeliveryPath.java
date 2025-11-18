package uk.ac.ed.acp.cw2.dto;

import java.util.List;

public record DeliveryPath(
        int deliveryId,
        List<LngLat> flightPath
) {}
