package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.ed.acp.cw2.model.LngLat;
import java.net.URL;
import uk.ac.ed.acp.cw2.model.Euclidian_distance;
import uk.ac.ed.acp.cw2.model.deserialization;

/**
 * Controller class that handles various HTTP endpoints for the application.
 * Provides functionality for serving the index page, retrieving a static UUID,
 * and managing key-value pairs through POST requests.
 */


// test data:

//{
//        "position1": {
//        "lng": -3.192473,
//        "lat": 55.946233
//        },
//        "position2": {
//        "lng": -3.192473,
//        "lat": 55.946233
//        }
//        }

//    {
//            "start": {
//            "lng": -3.192473,
//            "lat": 55.946233
//            },
//            "angle": 45
//    }


@RestController()
@RequestMapping("/api/v1")
public class ServiceController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceController.class);

    @Value("${ilp.service.url}")
    public URL serviceUrl;


    @GetMapping("/")
    public String index() {
        return "<html><body>" +
                "<h1>Welcome from ILP</h1>" +
                "<h4>ILP-REST-Service-URL:</h4> <a href=\"" + serviceUrl + "\" target=\"_blank\"> " + serviceUrl+ " </a>" +
                "</body></html>";
    }

    @GetMapping("/uid")
    public String uid() {
        return "s2532596";
    }


    @PostMapping("/distanceTo")
    public double distance(@RequestBody deserialization.PairRequest req) {
        if (req.position1() == null || req.position2() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "position1 and position2 are required");
        }
        return Euclidian_distance.distance(req.position1(), req.position2());
    }

    @PostMapping("/isCloseTo")
    public boolean isCloseTo(@RequestBody deserialization.PairRequest req) {
        if (req.position1() == null || req.position2() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "position1 and position2 are required");
        }
        return Euclidian_distance.isClose(req.position1(), req.position2());
    }


    @PostMapping("/nextPosition")
    public LngLat next(@Valid @RequestBody deserialization.StepByAngleRequest req) {
        if (req.start() == null || req.angle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start and angle are required");
        }
        var dir = Euclidian_distance.Direction16.angle_direction(req.angle());
        return dir.stepFrom(req.start());  // return LngLat directly
    }


    @PostMapping("/isInRegion")
    public boolean isInRegion() {
        return false;
    }
}



