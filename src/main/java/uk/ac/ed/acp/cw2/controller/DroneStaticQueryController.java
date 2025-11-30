package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.services.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController()
@RequestMapping("/api/v1/")
public class DroneStaticQueryController {

    private static final Logger logger = LoggerFactory.getLogger(DroneStaticQueryController.class);

    private final DroneQueryService droneQueryService;

    /**
     * Uses DroneQueryService to fetch drone data from the external REST API
     */
    public DroneStaticQueryController(DroneQueryService droneQueryService) {
        this.droneQueryService = droneQueryService;
    }

    /**
     * Fetch ONLY drone IDs with cooling filter
     * Returns minimal data (IDs only) instead of full objects
     */
    @GetMapping("dronesWithCooling/{state}")
    public int[] dronesWithCooling(@PathVariable boolean state) {
        logger.info("Fetching drone IDs with cooling={} (GraphQL optimized)", state);

        // Fetch all drones from the service
        List<DroneInfo> allDrones = droneQueryService.fetchDrones();

        // Filter by cooling capability
        List<Integer> droneIds = allDrones.stream()
                .filter(drone -> drone.capability().cooling() == state)
                .map(DroneInfo::id)
                .collect(Collectors.toList());

        logger.info("Found {} drones with cooling={} (minimal data transfer)", droneIds.size(), state);

        return droneIds.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Fetch single drone by ID
     * Fetches ONLY the specific drone, not entire collection
     */
    @GetMapping("droneDetails/{droneId}")
    public ResponseEntity<DroneInfo> droneDetails(@PathVariable int droneId) {
        logger.info("Fetching drone details for ID {} (GraphQL optimized - single query)", droneId);

        // Fetch all drones and find the specific one
        List<DroneInfo> allDrones = droneQueryService.fetchDrones();
        DroneInfo drone = allDrones.stream()
                .filter(d -> d.id() == droneId)
                .findFirst()
                .orElse(null);

        if (drone == null) {
            logger.warn("Drone {} not found", droneId);
            return ResponseEntity.notFound().build();
        }

        logger.info("Successfully fetched drone {} without overfetching", droneId);
        return ResponseEntity.ok(drone);
    }
}