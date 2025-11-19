package uk.ac.ed.acp.cw2.controller;

import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.MedDispatchRec;
import uk.ac.ed.acp.cw2.services.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.services.DroneQueryService;
import java.util.List;

@RestController()
@RequestMapping("/api/v1")
public class DroneAvailabilityController {

    private final DroneAvailabilityService availabilityService;
    public DroneAvailabilityController(DroneQueryService droneService, DroneAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping("/queryAvailableDrones")
    public int[] queryAvailableDrones(@RequestBody List<MedDispatchRec> dispatches) {
        return availabilityService.queryAvailableDrones(dispatches);
    }
}