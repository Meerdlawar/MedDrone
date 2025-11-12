package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.*;
import uk.ac.ed.acp.cw2.services.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller class that handles various HTTP endpoints for the application.
 * Provides functionality for serving the index page, retrieving a static UUID,
 * and managing key-value pairs through POST requests.
 */



@RestController()
@RequestMapping("/api/v1/")
public class DroneStaticQueryController {

    private static final Logger logger = LoggerFactory.getLogger(DroneStaticQueryController.class);

    private final DroneService droneService;

    public DroneStaticQueryController(DroneService droneService) {
        this.droneService = droneService;
    }

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


    @GetMapping("droneDetails/{droneid}")
    public ResponseEntity<DroneInfo> droneDetails(@PathVariable int droneid) {
        List<DroneInfo> drones = droneService.fetchDrones();

        for (DroneInfo drone : drones) {
            if (drone.id() == droneid) {
                DroneInfo dto = new DroneInfo(drone.name(), drone.id(), drone.capability());
                return ResponseEntity.ok(dto);
            }
        }
        return ResponseEntity.notFound().build();
    }
}