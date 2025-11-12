package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.services.*;

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


    public ServiceController(@Qualifier("endPoint") String endPoint, DroneService droneService) {
        this.serviceUrl = endPoint;
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

}
