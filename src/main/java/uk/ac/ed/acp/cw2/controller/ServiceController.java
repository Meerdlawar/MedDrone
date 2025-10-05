package uk.ac.ed.acp.cw2.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.model.LngLat;
import java.util.Map;
import java.lang.Math;
import java.net.URL;
import java.time.Instant;

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
    public double distanceTo(@RequestBody Map<String, LngLat> positions) {
        LngLat position1 = positions.get("position1");
        LngLat position2 = positions.get("position2");

        // The LngLat constructor already handles validation, so if creation fails,
        // Spring will automatically return a 400 Bad Request error.

        double lng1 = position1.getLng();
        double lat1 = position1.getLat();
        double lng2 = position2.getLng();
        double lat2 = position2.getLat();

        double deltaLng = lng1 - lng2;
        double deltaLat = lat1 - lat2;

        return Math.sqrt(deltaLng * deltaLng + deltaLat * deltaLat);
    }

    @PostMapping("/isCloseTo")
    public boolean isCloseTo(@RequestBody Map<String, LngLat> positions) {
        LngLat position1 = positions.get("position1");
        LngLat position2 = positions.get("position2");

        // The LngLat constructor already handles validation, so if creation fails,
        // Spring will automatically return a 400 Bad Request error.

        double lng1 = position1.getLng();
        double lat1 = position1.getLat();
        double lng2 = position2.getLng();
        double lat2 = position2.getLat();

        double deltaLng = lng1 - lng2;
        double deltaLat = lat1 - lat2;

        return Math.sqrt(deltaLng * deltaLng + deltaLat * deltaLat) < 0.00015;
    }


    public record NextPositionRequest(LngLat start, double angle) { }
    @PostMapping("/nextPosition")
    public LngLat nextPosition(@RequestBody NextPositionRequest req) {
        LngLat start = req.start();
        double angle = req.angle();

        if (angle % 22.5 != 0) {
            throw new IllegalArgumentException("Angle must be a multiple of 22.5");
        }

        double step = 0.00015;
        double deltaLng = Math.cos(Math.toRadians(angle)) * step;
        double deltaLat = Math.sin(Math.toRadians(angle)) * step;


        return new LngLat(start.getLng() + deltaLng, start.getLat() + deltaLat);
    }

    @PostMapping("/isInRegion")
    public boolean isInRegion() {
        return false;
    }


}


