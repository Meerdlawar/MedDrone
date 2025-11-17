package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.*;
import uk.ac.ed.acp.cw2.services.*;
import java.util.ArrayList;
import java.util.List;

@RestController()
@RequestMapping("/api/v1/")
public class DroneStaticQueryController {

    private static final Logger logger = LoggerFactory.getLogger(DroneStaticQueryController.class);

    private final DroneQueryService droneService;
    public DroneStaticQueryController(DroneQueryService droneService) {
        this.droneService = droneService;
    }

    // Retrieves Json details about drones and checks if it matches the query
    @GetMapping("droneswithCooling/{state}")
    public int[] dronesWithCooling(@PathVariable boolean state) {
        List<DroneInfo> drones = droneService.fetchDrones();
        List<Integer> droneId = new ArrayList<>();
        for (DroneInfo drone : drones) {
            if (drone.capability().cooling() == state) {
                droneId.add(drone.id());
            }
        }
        return droneId.stream().mapToInt(i -> i).toArray();
    }

    // Retrieves Json details about drones for that particular droneID
    @GetMapping("droneDetails/{droneId}")
    public ResponseEntity<DroneInfo> droneDetails(@PathVariable int droneId) {
        List<DroneInfo> drones = droneService.fetchDrones();

        for (DroneInfo drone : drones) {
            if (drone.id() == droneId) {
                DroneInfo dto = new DroneInfo(drone.name(), drone.id(), drone.capability());
                return ResponseEntity.ok(dto);
            }
        }
        return ResponseEntity.notFound().build();
    }
}