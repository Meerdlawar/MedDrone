package uk.ac.ed.acp.cw2.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.graphql.model.*;
import uk.ac.ed.acp.cw2.services.DroneQueryService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DroneQueryResolver {

    private final DroneQueryService droneQueryService;

    public DroneQueryResolver(DroneQueryService droneQueryService) {
        this.droneQueryService = droneQueryService;
    }

    @QueryMapping
    public List<DroneInfo> drones(
            @Argument DroneFilters filters,
            @Argument OrderBy orderBy,
            @Argument Integer limit) {

        // Fetch all drones
        List<DroneInfo> allDrones = droneQueryService.fetchDrones();

        // Apply filters
        List<DroneInfo> filtered = applyFilters(allDrones, filters);

        // Apply ordering
        if (orderBy != null) {
            filtered = applyOrdering(filtered, orderBy);
        }

        // Apply limit
        if (limit != null && limit > 0) {
            filtered = filtered.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        return filtered;
    }

    @QueryMapping
    public DroneInfo drone(@Argument int id) {
        return droneQueryService.fetchDrones().stream()
                .filter(d -> d.id() == id)
                .findFirst()
                .orElse(null);
    }

    @QueryMapping
    public List<ServicePoints> servicePoints(@Argument Location near) {
        List<ServicePoints> allServicePoints = droneQueryService.fetchServicePoints();

        if (near == null) {
            return allServicePoints;
        }

        // Filter by distance if near is specified
        return allServicePoints.stream()
                .filter(sp -> {
                    double distance = calculateDistance(
                            sp.location().lng(), sp.location().lat(),
                            near.getLng(), near.getLat()
                    );
                    return distance <= (near.getRadiusDegrees() != null ? near.getRadiusDegrees() : 0.01);
                })
                .collect(Collectors.toList());
    }

    // Field resolver for currentServicePoint
    @SchemaMapping(typeName = "Drone", field = "currentServicePoint")
    public ServicePoints currentServicePoint(DroneInfo drone) {
        LngLat origin = droneQueryService.fetchDroneOrigin(drone.id()).orElse(null);
        if (origin == null) return null;

        return droneQueryService.fetchServicePoints().stream()
                .filter(sp -> sp.location().equals(origin))
                .findFirst()
                .orElse(null);
    }

    // Field resolver for availability
    @SchemaMapping(typeName = "Drone", field = "availability")
    public List<DroneAvailability> availability(DroneInfo drone) {
        List<DronesForServicePoints> allAvailability = droneQueryService.fetchDroneAvailability();

        for (DronesForServicePoints sp : allAvailability) {
            for (ListDrones ld : sp.drones()) {
                if (ld.id() == drone.id()) {
                    return ld.availability();
                }
            }
        }

        return List.of();
    }

    // Field resolver for estimatedCost
    @SchemaMapping(typeName = "Drone", field = "estimatedCost")
    public Double estimatedCost(DroneInfo drone, @Argument int distance) {
        DroneCapability cap = drone.capability();
        return cap.costInitial() + cap.costFinal() + (distance * cap.costPerMove());
    }

    // Field resolver for availableDrones on ServicePoint
    @SchemaMapping(typeName = "ServicePoint", field = "availableDrones")
    public List<DroneInfo> availableDrones(ServicePoints servicePoint, @Argument String time) {
        List<DronesForServicePoints> availability = droneQueryService.fetchDroneAvailability();
        List<DroneInfo> allDrones = droneQueryService.fetchDrones();

        // Find drones at this service point
        DronesForServicePoints spDrones = availability.stream()
                .filter(sp -> sp.servicePointId() == servicePoint.id())
                .findFirst()
                .orElse(null);

        if (spDrones == null) return List.of();

        LocalTime queryTime = time != null && !time.equals("now")
                ? LocalTime.parse(time)
                : LocalTime.now();
        String currentDay = DayOfWeek.from(java.time.LocalDate.now()).name();

        List<Integer> availableDroneIds = new ArrayList<>();

        for (ListDrones ld : spDrones.drones()) {
            boolean isAvailable = ld.availability().stream()
                    .anyMatch(a ->
                            a.dayOfWeek().equals(currentDay) &&
                                    queryTime.isAfter(a.from()) &&
                                    queryTime.isBefore(a.until())
                    );

            if (isAvailable) {
                availableDroneIds.add(ld.id());
            }
        }

        return allDrones.stream()
                .filter(d -> availableDroneIds.contains(d.id()))
                .collect(Collectors.toList());
    }

    // Helper methods
    private List<DroneInfo> applyFilters(List<DroneInfo> drones, DroneFilters filters) {
        if (filters == null) return drones;

        return drones.stream()
                .filter(drone -> matchesCapabilityFilter(drone, filters.getCapability()))
                .filter(drone -> matchesCostFilter(drone, filters.getCost()))
                .filter(drone -> matchesAvailabilityFilter(drone, filters.getAvailability()))
                .collect(Collectors.toList());
    }

    private boolean matchesCapabilityFilter(DroneInfo drone, CapabilityFilter filter) {
        if (filter == null) return true;

        DroneCapability cap = drone.capability();

        if (filter.getMinCapacity() != null && cap.capacity() < filter.getMinCapacity()) {
            return false;
        }
        if (filter.getMaxCapacity() != null && cap.capacity() > filter.getMaxCapacity()) {
            return false;
        }
        if (filter.getCooling() != null && cap.cooling() != filter.getCooling()) {
            return false;
        }
        if (filter.getHeating() != null && cap.heating() != filter.getHeating()) {
            return false;
        }

        return true;
    }

    private boolean matchesCostFilter(DroneInfo drone, CostFilter filter) {
        if (filter == null) return true;

        DroneCapability cap = drone.capability();

        if (filter.getMaxCostPerMove() != null && cap.costPerMove() > filter.getMaxCostPerMove()) {
            return false;
        }
        if (filter.getMaxCostInitial() != null && cap.costInitial() > filter.getMaxCostInitial()) {
            return false;
        }
        if (filter.getMaxCostFinal() != null && cap.costFinal() > filter.getMaxCostFinal()) {
            return false;
        }

        return true;
    }

    private boolean matchesAvailabilityFilter(DroneInfo drone, AvailabilityFilter filter) {
        if (filter == null) return true;

        List<DroneAvailability> availability = availability(drone);

        if (filter.getDayOfWeek() == null && filter.getTime() == null) {
            return true;
        }

        LocalTime queryTime = filter.getTime() != null ? LocalTime.parse(filter.getTime()) : null;

        return availability.stream().anyMatch(a -> {
            boolean dayMatches = filter.getDayOfWeek() == null ||
                    a.dayOfWeek().equals(filter.getDayOfWeek());

            boolean timeMatches = queryTime == null ||
                    (queryTime.isAfter(a.from()) && queryTime.isBefore(a.until()));

            return dayMatches && timeMatches;
        });
    }

    private List<DroneInfo> applyOrdering(List<DroneInfo> drones, OrderBy orderBy) {
        Comparator<DroneInfo> comparator = switch (orderBy.getField()) {
            case "COST_PER_MOVE" -> Comparator.comparing(d -> d.capability().costPerMove());
            case "COST_INITIAL" -> Comparator.comparing(d -> d.capability().costInitial());
            case "COST_FINAL" -> Comparator.comparing(d -> d.capability().costFinal());
            case "CAPACITY" -> Comparator.comparing(d -> d.capability().capacity());
            case "MAX_MOVES" -> Comparator.comparing(d -> d.capability().maxMoves());
            default -> Comparator.comparing(DroneInfo::id);
        };

        if ("DESC".equals(orderBy.getDirection())) {
            comparator = comparator.reversed();
        }

        return drones.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        return Math.hypot(lng1 - lng2, lat1 - lat2);
    }
}