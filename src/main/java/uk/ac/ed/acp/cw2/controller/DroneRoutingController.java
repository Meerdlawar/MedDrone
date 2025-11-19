package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.List;

@RestController()
@RequestMapping("/api/v1")
public class DroneRoutingController {

    @PostMapping("/calcDeliveryPath")
    public DeliveryPath calcDeliveryPath(@Valid @RequestBody List<MedDispatchRec> req) {
        return null;
    }

}