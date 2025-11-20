package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.services.DroneRoutingService;

import java.util.List;

@RestController()
@RequestMapping("/api/v1")
public class DroneRoutingController {

    private final DroneRoutingService droneRoutingService;
    private DroneRoutingController(DroneRoutingService droneRoutingService1) {
        this.droneRoutingService = droneRoutingService1;
    }

    @PostMapping("/calcDeliveryPath")
    public DeliveryPlan calcDeliveryPath(@Valid @RequestBody List<MedDispatchRec> req) {
        return droneRoutingService.calcDeliveryPlan(req);
    }

}