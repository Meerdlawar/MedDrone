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
@RequestMapping("/api/v1")
public class ServiceController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceController.class);

    private final String serviceUrl;
    private final DroneService droneService;

    public ServiceController(@Qualifier("ilpEndPoint") String ilpEndPoint, DroneService droneService) {
        this.serviceUrl = ilpEndPoint;
        this.droneService = droneService;
    }


    @GetMapping("/")
    public String index() {
        return "<html><body>" +
                "<h1>Welcome from ILP</h1>" +
                "<h4>ILP-REST-Service-URL:</h4> <a href=\"" + serviceUrl + "\" target=\"_blank\"> " + serviceUrl + " </a>" +
                "</body></html>";
    }

    @GetMapping("/uid")
    public String uid() {
        return "s2532596";
    }


    @PostMapping("/distanceTo")
    public double distance(@Valid @RequestBody PairRequest req) {
        return DroneNavigation.distance(req.position1(), req.position2());
    }

    @PostMapping("/isCloseTo")
    public boolean isCloseTo(@Valid @RequestBody PairRequest req) {
        return DroneNavigation.isClose(req.position1(), req.position2());
    }


    @PostMapping("/nextPosition")
    public LngLat next(@Valid @RequestBody StepByAngleRequest req) {
        var dir = DroneNavigation.Direction16.angle_direction(req.angle());
        return dir.stepFrom(req.start());  // return LngLat directly
    }


    @PostMapping("/isInRegion")
    public boolean isInRegion(@Valid @RequestBody LocationPayload req) {
        // Validate and build the point
        PositionRegion pos = req.position();
        LngLat point = new LngLat(pos.lng(), pos.lat());

        // Build vertices; each LngLat constructor enforces sane ranges / null checks
        java.util.List<LngLat> verts = new java.util.ArrayList<>();
        for (PositionRegion v : req.region().vertices()) {
            verts.add(new LngLat(v.lng(), v.lat()));
        }
        return PointInRegion.isInRegion(point, verts);
    }

    @GetMapping("/dronesWithCooling/{state}")
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

    @GetMapping("/dronesWithCooling/{droneid}")
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
