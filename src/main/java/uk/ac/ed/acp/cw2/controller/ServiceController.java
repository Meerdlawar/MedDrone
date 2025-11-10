package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.LngLat;
import uk.ac.ed.acp.cw2.services.*;
import java.net.URL;
import uk.ac.ed.acp.cw2.data.PairRequest;
import uk.ac.ed.acp.cw2.data.StepByAngleRequest;
import uk.ac.ed.acp.cw2.data.LocationPayload;
import uk.ac.ed.acp.cw2.data.PositionRegion;

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

    public ServiceController(String serviceUrl) {
        this.serviceUrl = serviceUrl;
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
    public double distance(@RequestBody PairRequest req) {
        return DroneNavigation.distance(req.position1(), req.position2());
    }

    @PostMapping("/isCloseTo")
    public boolean isCloseTo(@RequestBody PairRequest req) {
        return DroneNavigation.isClose(req.position1(), req.position2());
    }


    @PostMapping("/nextPosition")
    public LngLat next(@Valid @RequestBody StepByAngleRequest req) {
        var dir = DroneNavigation.Direction16.angle_direction(req.angle());
        return dir.stepFrom(req.start());  // return LngLat directly
    }


    @PostMapping("isInRegion")
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
}