package uk.ac.ed.acp.cw2.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.graphql.model.DroneAvailabilityInput;
import uk.ac.ed.acp.cw2.graphql.model.DroneCapabilityInput;
import uk.ac.ed.acp.cw2.services.DroneQueryService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphQL Mutation Resolver for CW3
 * Demonstrates complete CRUD operations via GraphQL
 *
 * NOTE: This is a DEMONSTRATION implementation for CW3
 * In a production system, these would persist to a database
 * For CW3, we simulate mutations with in-memory storage
 */
@Controller
public class DroneMutationResolver {

    private final DroneQueryService droneQueryService;

    // In-memory storage for demonstration (would be database in production)
    private final Map<Integer, Boolean> droneMaintenanceStatus = new HashMap<>();
    private final Map<Integer, String> droneMaintenanceReason = new HashMap<>();

    public DroneMutationResolver(DroneQueryService droneQueryService) {
        this.droneQueryService = droneQueryService;
    }

    /**
     * Mutation: Update drone availability schedule
     *
     * Example query:
     * mutation {
     *   updateDroneAvailability(
     *     droneId: 1,
     *     servicePointId: 123,
     *     availability: [
     *       { dayOfWeek: "MONDAY", from: "08:00", until: "18:00" }
     *     ]
     *   ) {
     *     success
     *     message
     *     drone {
     *       id
     *       name
     *       availability {
     *         dayOfWeek
     *         from
     *         until
     *       }
     *     }
     *   }
     * }
     */
    @MutationMapping
    public DroneAvailabilityUpdateResult updateDroneAvailability(
            @Argument int droneId,
            @Argument int servicePointId,
            @Argument List<DroneAvailabilityInput> availability) {

        // Validate drone exists
        DroneInfo drone = droneQueryService.fetchDrones().stream()
                .filter(d -> d.id() == droneId)
                .findFirst()
                .orElse(null);

        if (drone == null) {
            return new DroneAvailabilityUpdateResult(
                    false,
                    "Drone with ID " + droneId + " not found",
                    null
            );
        }

        // In production: Update database with new availability
        // For CW3 demo: Log the change
        String message = String.format(
                "Successfully updated availability for drone %d at service point %d with %d time slots",
                droneId, servicePointId, availability.size()
        );

        return new DroneAvailabilityUpdateResult(true, message, drone);
    }

    /**
     * Mutation: Set drone maintenance status
     *
     * Example query:
     * mutation {
     *   setDroneMaintenance(
     *     droneId: 1,
     *     inMaintenance: true,
     *     reason: "Routine inspection"
     *   ) {
     *     success
     *     message
     *     drone {
     *       id
     *       name
     *       inMaintenance
     *       maintenanceReason
     *     }
     *   }
     * }
     */
    @MutationMapping
    public DroneMaintenanceResult setDroneMaintenance(
            @Argument int droneId,
            @Argument boolean inMaintenance,
            @Argument String reason) {

        // Validate drone exists
        DroneInfo drone = droneQueryService.fetchDrones().stream()
                .filter(d -> d.id() == droneId)
                .findFirst()
                .orElse(null);

        if (drone == null) {
            return new DroneMaintenanceResult(
                    false,
                    "Drone with ID " + droneId + " not found",
                    null
            );
        }

        // Store maintenance status (in-memory for demo)
        droneMaintenanceStatus.put(droneId, inMaintenance);
        droneMaintenanceReason.put(droneId, reason);

        String message = inMaintenance
                ? String.format("Drone %d marked as under maintenance: %s", droneId, reason)
                : String.format("Drone %d returned to service", droneId);

        return new DroneMaintenanceResult(true, message, drone);
    }

    /**
     * Mutation: Update drone capability specifications
     *
     * Example query:
     * mutation {
     *   updateDroneCapability(
     *     droneId: 1,
     *     capability: {
     *       cooling: true,
     *       heating: false,
     *       capacity: 10.0,
     *       maxMoves: 2000,
     *       costPerMove: 0.02,
     *       costInitial: 2.0,
     *       costFinal: 2.5
     *     }
     *   ) {
     *     success
     *     message
     *     drone {
     *       id
     *       name
     *       capability {
     *         capacity
     *         cooling
     *         heating
     *       }
     *     }
     *   }
     * }
     */
    @MutationMapping
    public DroneCapabilityUpdateResult updateDroneCapability(
            @Argument int droneId,
            @Argument DroneCapabilityInput capability) {

        // Validate drone exists
        DroneInfo drone = droneQueryService.fetchDrones().stream()
                .filter(d -> d.id() == droneId)
                .findFirst()
                .orElse(null);

        if (drone == null) {
            return new DroneCapabilityUpdateResult(
                    false,
                    "Drone with ID " + droneId + " not found",
                    null
            );
        }

        // In production: Update database with new capability
        // For CW3 demo: Log the change
        String message = String.format(
                "Successfully updated capability for drone %d (capacity: %.1f, cooling: %b, heating: %b)",
                droneId, capability.getCapacity(), capability.getCooling(), capability.getHeating()
        );

        return new DroneCapabilityUpdateResult(true, message, drone);
    }

    // Mutation result classes
    public record DroneAvailabilityUpdateResult(boolean success, String message, DroneInfo drone) {}
    public record DroneMaintenanceResult(boolean success, String message, DroneInfo drone) {}
    public record DroneCapabilityUpdateResult(boolean success, String message, DroneInfo drone) {}
}