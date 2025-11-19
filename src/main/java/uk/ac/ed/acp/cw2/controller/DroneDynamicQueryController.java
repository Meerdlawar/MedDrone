package uk.ac.ed.acp.cw2.controller;

import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.MedDispatchRec;
import uk.ac.ed.acp.cw2.dto.QueryAttributes;
import uk.ac.ed.acp.cw2.services.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.services.DroneQueryService;
import java.util.ArrayList;
import java.util.List;

@RestController()
@RequestMapping("/api/v1")
public class DroneDynamicQueryController {

    private final DroneQueryService droneService;
    private final DroneAvailabilityService availabilityService;
    public DroneDynamicQueryController(DroneQueryService droneService, DroneAvailabilityService availabilityService) {
        this.droneService = droneService;
        this.availabilityService = availabilityService;
    }

    @GetMapping("/queryAsPath/{attributeName}/{attributeValue}")
    public int[] queryAsPath(@PathVariable String attributeName, @PathVariable String attributeValue) {
        List<QueryAttributes> reqs = new ArrayList<>();
        QueryAttributes req = new QueryAttributes(attributeName, "=", attributeValue);
        reqs.add(req);
        return droneService.filterDroneAttributes(reqs);
    }

    @PostMapping("/query")
    public int[] query(@RequestBody List<QueryAttributes> reqs) {
        return droneService.filterDroneAttributes(reqs);
    }
}