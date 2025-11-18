package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.services.*;
import uk.ac.ed.acp.cw2.services.DroneNavigation;

@RestController()
@RequestMapping("/api/v1")
public class GeometryController {

    private static final Logger logger = LoggerFactory.getLogger(GeometryController.class);

    @PostMapping("/distanceTo")
    public double distance(@Valid @RequestBody PosOnePosTwo req) {
        return DroneNavigation.distance(req.position1(), req.position2());
    }

    @PostMapping("/isCloseTo")
    public boolean isCloseTo(@Valid @RequestBody PosOnePosTwo req) {
        return DroneNavigation.isClose(req.position1(), req.position2());
    }

    @PostMapping("/nextPosition")
    public LngLat nextPosition(@Valid @RequestBody NextPosition req) {
        return DroneNavigation.nextPosition(req.start(), req.angle());
    }

    @PostMapping("/isInRegion")
    public boolean isInRegion(@Valid @RequestBody IsInRegion req) {
        // Validate and build the point
        PositionRegion pos = req.position();
        LngLat point = new LngLat(pos.lng(), pos.lat());

        java.util.List<LngLat> verts = new java.util.ArrayList<>();
        for (PositionRegion v : req.region().vertices()) {
            verts.add(new LngLat(v.lng(), v.lat()));
        }
        return DronePointInRegion.isInRegion(point, verts);
    }
}
