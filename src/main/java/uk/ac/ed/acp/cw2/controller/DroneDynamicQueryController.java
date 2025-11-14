package uk.ac.ed.acp.cw2.controller;

import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.DispatchRequirements;
import uk.ac.ed.acp.cw2.data.MedDispatchRec;
import uk.ac.ed.acp.cw2.data.QueryAttributes;
import uk.ac.ed.acp.cw2.services.DroneService;
import java.time.LocalDate;
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
    public int[] queryAvailableDrones(@RequestBody List<MedDispatchRec> dispatches) {
        List<QueryAttributes> reqs = new ArrayList<>();

        for (MedDispatchRec dispatch : dispatches) {
            DispatchRequirements requirements = dispatch.requirements();

            if (requirements == null) {
                continue;
            }


            reqs.add(new QueryAttributes(
                    "capacity",
                    "=",
                    String.valueOf(requirements.capacity())
            ));



            // Example: only require cooling if the dispatch needs it
            if (requirements.cooling() == true) {
                reqs.add(new QueryAttributes(
                        "cooling",
                        "=",
                        "true"
                ));
            }

            // Example: only require heating if the dispatch needs it
            if (requirements.heating() == true) {
                reqs.add(new QueryAttributes(
                        "heating",
                        "=",
                        "true"
                ));
            }

            if (requirements.maxCost() != null) {
                reqs.add(new QueryAttributes(
                        "capacity",
                        "<",
                        String.valueOf(requirements.maxCost())
                ));
            }



            // other constraints
            // - date
            // - time
        }

        // Reuse the existing filtering logic in DroneService
        return droneService.filterDroneAttributes(reqs);
    }

}
