package uk.ac.ed.acp.cw2.controller;

import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.services.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.services.DroneQueryService;
import uk.ac.ed.acp.cw2.services.DroneRoutingService;

@RestController()
@RequestMapping("/api/v1")
public class DroneRoutingController {

    private final DroneQueryService droneService;
    private final DroneAvailabilityService availabilityService;
    private final DroneRoutingService routingService;

    public DroneRoutingController(DroneQueryService droneService, DroneAvailabilityService availabilityService,
                                       DroneRoutingService routingService) {
        this.droneService = droneService;
        this.availabilityService = availabilityService;
        this.routingService = routingService;
    }
}