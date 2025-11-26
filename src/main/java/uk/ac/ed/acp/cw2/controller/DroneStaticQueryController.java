package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.services.*;
import java.util.ArrayList;
import java.util.List;

@RestController()
@RequestMapping("/api/v1/")
public class DroneStaticQueryController {

    private static final Logger logger = LoggerFactory.getLogger(DroneStaticQueryController.class);

    private final GraphQLDataService graphQLDataService;

    // OPTIMIZED: Use GraphQLDataService instead of DroneQueryService
    // This reduces overfetching by using GraphQL queries
    public DroneStaticQueryController(GraphQLDataService graphQLDataService) {
        this.graphQLDataService = graphQLDataService;
    }

    /**
     * OPTIMIZED: Uses GraphQL query with server-side filtering
     * Before: Fetched ALL drones, filtered client-side, returned only IDs
     * After: GraphQL query requests only what's needed
     */
    @GetMapping("dronesWithCooling/{state}")
    public int[] dronesWithCooling(@PathVariable boolean state) {
        logger.info("Fetching drones with cooling={} via GraphQL", state);

        // Use GraphQL query to fetch only drones with the specified cooling capability
        // This avoids fetching all drone data when we only need IDs
        List<DroneInfo> drones = graphQLDataService.fetchDronesWithFilters(state, null, null, null);

        return drones.stream()
                .mapToInt(DroneInfo::id)
                .toArray();
    }

    /**
     * OPTIMIZED: Uses GraphQL query to fetch single drone by ID
     * Before: Fetched ALL drones to find one
     * After: GraphQL query fetches only the specific drone
     */
    @GetMapping("droneDetails/{droneId}")
    public ResponseEntity<DroneInfo> droneDetails(@PathVariable int droneId) {
        logger.info("Fetching drone details for ID {} via GraphQL", droneId);

        // Use GraphQL query to fetch only the specific drone
        // This avoids fetching all drones when we only need one
        DroneInfo drone = graphQLDataService.fetchDroneById(droneId);

        if (drone == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(drone);
    }
}