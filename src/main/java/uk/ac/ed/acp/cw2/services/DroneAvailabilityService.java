package uk.ac.ed.acp.cw2.services;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.dto.DispatchRequirements;
import uk.ac.ed.acp.cw2.dto.DroneAvailability;
import uk.ac.ed.acp.cw2.dto.DroneInfo;
import uk.ac.ed.acp.cw2.dto.DronesForServicePoints;
import uk.ac.ed.acp.cw2.dto.ListDrones;
import uk.ac.ed.acp.cw2.dto.MedDispatchRec;
import uk.ac.ed.acp.cw2.dto.QueryAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class DroneAvailabilityService {
    DroneQueryService droneService;

    private DroneAvailabilityService(DroneQueryService droneService) {
        this.droneService = droneService;
    }

    public int[] queryAvailableDrones(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return new int[0];
        }

        double maxRequiredCapacity = 0.0;
        boolean needsCooling = false;
        boolean needsHeating = false;

        for (MedDispatchRec dispatch : dispatches) {
            DispatchRequirements r = dispatch.requirements();
            if (r == null) continue;

            maxRequiredCapacity += r.capacity();

            if (r.cooling()) needsCooling = true;
            if (r.heating()) needsHeating = true;
        }

        List<QueryAttributes> reqs = new ArrayList<>();

        if (maxRequiredCapacity > 0) {
            reqs.add(new QueryAttributes(
                    "capacity",
                    ">",
                    String.valueOf(maxRequiredCapacity)
            ));
        }

        if (needsCooling) {
            reqs.add(new QueryAttributes("cooling", "=", "true"));
        }
        if (needsHeating) {
            reqs.add(new QueryAttributes("heating", "=", "true"));
        }

        int[] attributeMatched = droneService.filterDroneAttributes(reqs);
        if (attributeMatched.length == 0) {
            return new int[0];
        }

        // Get the strictest maxCost requirement
        Double minMaxCost = dispatches.stream()
                .map(d -> d.requirements().maxCost())
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);

        // Post-filter by maxCost if specified
        if (minMaxCost != null) {
            List<DroneInfo> allDrones = droneService.fetchDrones();
            final Double costLimit = minMaxCost;
            attributeMatched = Arrays.stream(attributeMatched)
                    .filter(id -> allDrones.stream()
                            .anyMatch(d -> d.id() == id && d.capability().costPerMove() < costLimit))
                    .toArray();
        }

        if (attributeMatched.length == 0) {
            return new int[0];
        }

        // Filter by availability
        return filterByAvailability(attributeMatched, dispatches);
    }

    private int[] filterByAvailability(int[] drones, List<MedDispatchRec> dispatches) {
        List<DronesForServicePoints> all = droneService.fetchDroneAvailability();

        List<Integer> result = new ArrayList<>();

        for (int droneId : drones) {
            List<DroneAvailability> availability = findAvailabilityForDrone(droneId, all);
            if (availability.isEmpty()) continue;

            if (isDroneAvailableForAllDispatches(availability, dispatches)) {
                result.add(droneId);
            }
        }

        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private List<DroneAvailability> findAvailabilityForDrone(int droneId, List<DronesForServicePoints> all) {
        for (DronesForServicePoints sps : all) {
            if (sps.drones() == null) continue;

            for (ListDrones ld : sps.drones()) {
                if (ld.id() == droneId) {
                    return ld.availability();
                }
            }
        }
        return List.of();
    }

    private boolean isDroneAvailableForAllDispatches(List<DroneAvailability> slots, List<MedDispatchRec> dispatches) {
        for (MedDispatchRec d : dispatches) {
            if (!isAvailableForDispatch(slots, d)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAvailableForDispatch(List<DroneAvailability> slots, MedDispatchRec dispatch) {
        LocalDate date = dispatch.date();
        LocalTime time = dispatch.time();

        if (date == null && time == null) {
            return true;
        }

        String dispatchDay;
        if (date != null) {
            dispatchDay = date.getDayOfWeek().name();
        } else {
            dispatchDay = null;
        }

        for (DroneAvailability a : slots) {
            if (dispatchDay != null &&
                    !dispatchDay.equalsIgnoreCase(a.dayOfWeek())) {
                continue;
            }

            if (time != null) {
                if (time.isBefore(a.from()) || time.isAfter(a.until())) {
                    continue;
                }
            }

            return true;
        }

        return false;
    }
}