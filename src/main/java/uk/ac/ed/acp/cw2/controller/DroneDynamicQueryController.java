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

//    @PostMapping("/query")
//    public int[] query(List<QueryAttributes> reqs) {
//        // We take the list of queries
//        // We take each query in the list and call queryAttribute, this returns a list
//        // Then we iterate through the list and call
//        // We do this over and over using the same list
//        for (QueryAttributes req: reqs) {
//            int curr[] = droneService.queryAttribute(req.attribute(), req.operator(), req.value());
//
//        }
//    }

}
