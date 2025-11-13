package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.MedDispatchRec;
import uk.ac.ed.acp.cw2.data.QueryAttributes;
import uk.ac.ed.acp.cw2.services.DroneService;

import java.util.ArrayList;
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
        List<QueryAttributes> reqs = new ArrayList<QueryAttributes>();
        QueryAttributes req = new QueryAttributes(attributeName, "=", attributeValue);
        reqs.add(req);
        return droneService.filterDroneAttributes(reqs);
    }

    @PostMapping("/query")
    public int[] query(@RequestBody List<QueryAttributes> reqs) {
        return droneService.filterDroneAttributes(reqs);
    }

    @PostMapping("/queryAvailableDrones")
    public int[] queryAvailableDrones(@RequestBody List<MedDispatchRec> recs) {
        // required:
        //      - ID
        //      - Requirements:
        //          - capacity
        // Drone needs to meet DispatchRequirements
        List<Integer> out = new ArrayList<>();
        for (MedDispatchRec rec : recs) {

        }

        // Drone meets availability on certain days


        return null;
    }

}
