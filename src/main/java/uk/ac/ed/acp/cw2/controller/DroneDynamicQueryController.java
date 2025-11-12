package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import uk.ac.ed.acp.cw2.data.DroneInfo;
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

    @GetMapping("/queryAsPath/{attribute-name}/{attribute-value}")
    public int[] queryAsPath(@PathVariable String attributeName, @PathVariable String attributeValue) {
        List<DroneInfo> drones = droneService.fetchDrones();
        List<Integer> droneId = new ArrayList<>();
        for (DroneInfo drone: drones) {
//            if (drone.capability().attributeName == attributeValue) {
//                droneId.add(drone.id());
//            }
            switch (attributeName) {
                case "cooling":
//                    if (drone.capability().cooling() == attributeValue) {
//                        droneId.add(drone.id());
//                    }
                    break;
                case "heating":
//                    if (drone.capability().cooling() == attributeValue) {
//                        droneId.add(drone.id());
//                    }
                    break;
                case "capacity":
//                    if (drone.capability().cooling() == attributeValue) {
//                        droneId.add(drone.id());
//                    }
                    break;

                case "maxMoves":
//                    if (drone.capability().cooling() == attributeValue) {
//                        droneId.add(drone.id());
//                    }
                    break;
                case "costPerMove":
//                    if (drone.capability().cooling() == attributeValue) {
//                        droneId.add(drone.id());
//                    }
                    break;
                case "costInitial":
//                    if (drone.capability().cooling() == attributeValue) {
//                        droneId.add(drone.id());
//                    }
                    break;
                case "costFinal":
//                    if (drone.capability().cooling() == attributeValue) {
//                        droneId.add(drone.id());
//                    }
                    break;
            }
        }
        return droneId.stream().mapToInt(i -> i).toArray();
    }

}
