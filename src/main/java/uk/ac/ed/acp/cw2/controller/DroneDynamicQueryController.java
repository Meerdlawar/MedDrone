package uk.ac.ed.acp.cw2.controller;

import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.QueryAttributes;
import uk.ac.ed.acp.cw2.services.DroneService;
import java.util.List;

@RestController()
@RequestMapping("/api/v1")
public class DroneDynamicQueryController {

    private final DroneService droneService;
    public DroneDynamicQueryController(DroneService droneService) {
        this.droneService = droneService;
    }

    @GetMapping("/queryAsPath/{attributeName}/{attributeValue}")
    public int[] queryAsPath(@PathVariable String attributeName, @PathVariable String attributeValue) {
        return droneService.queryAttribute(attributeName, "=" , attributeValue);
    }

    @PostMapping("/query")
    public int[] query(@RequestBody List<QueryAttributes> reqs) {
        return droneService.filterDroneAttributes(reqs);
    }
}
