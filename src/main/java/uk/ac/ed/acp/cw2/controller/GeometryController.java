package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.data.*;
import uk.ac.ed.acp.cw2.services.*;

/**
 * Controller class that handles various HTTP endpoints for the application.
 * Provides functionality for serving the index page, retrieving a static UUID,
 * and managing key-value pairs through POST requests.
 */


@RestController()
@RequestMapping("/api/v1")
public class GeometryController {

    private static final Logger logger = LoggerFactory.getLogger(GeometryController.class);


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
}
