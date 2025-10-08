package uk.ac.ed.acp.cw2.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.model.LngLat;
import java.net.URL;
import uk.ac.ed.acp.cw2.model.Euclidian_distance;
import uk.ac.ed.acp.cw2.model.dto;
import org.json.*;

/**
 * Controller class that handles various HTTP endpoints for the application.
 * Provides functionality for serving the index page, retrieving a static UUID,
 * and managing key-value pairs through POST requests.
 */
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
    public dto.DistanceResponse distance(@RequestBody dto.PairRequest req) {
        return new dto.DistanceResponse(Euclidian_distance.distance(req.position1(), req.position2()));
    }

    @PostMapping("/isCloseTo")
    public dto.IsCloseResponse isCloseTo(@RequestBody dto.PairRequest req) {
        return new dto.IsCloseResponse(Euclidian_distance.isClose(req.position1(), req.position2()));
    }


    public record NextPositionRequest(LngLat start, double angleDeg) {}
    public record NextPositionResponse(LngLat position) {}
    @PostMapping("/nextPosition")
    public NextPositionResponse next(@Valid @RequestBody dto.StepByAngleRequest req) {
        var dir = Euclidian_distance.Direction16.angle_direction(req.angle()); // see section 2
        var pos = dir.stepFrom(req.start());                 // uses fixed STEP_SIZE internally
        return new NextPositionResponse(pos);
    }


    @PostMapping("/isInRegion")
    public boolean isInRegion() {
        return false;
    }
}


