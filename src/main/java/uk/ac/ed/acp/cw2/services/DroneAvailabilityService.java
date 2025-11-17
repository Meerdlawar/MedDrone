package uk.ac.ed.acp.cw2.services;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.DispatchRequirements;
import uk.ac.ed.acp.cw2.data.DroneAvailability;
import uk.ac.ed.acp.cw2.data.DronesForServicePoints;
import uk.ac.ed.acp.cw2.data.ListDrones;
import uk.ac.ed.acp.cw2.data.MedDispatchRec;
import uk.ac.ed.acp.cw2.data.QueryAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DroneAvailabilityService {
    DroneQueryService droneService;

    private DroneAvailabilityService(DroneQueryService droneService) {
        this.droneService = droneService;
    }

    /**
     * Returns all drone IDs that can satisfy ALL dispatches
     * (requirements AND date/time availability).
     */
    public int[] queryAvailableDrones(List<MedDispatchRec> dispatches) {
        if (dispatches == null || dispatches.isEmpty()) {
            return new int[0];
        }

        // 1) Build combined attribute constraints (AND across all dispatches)
        List<QueryAttributes> reqs = new ArrayList<>();
        for (MedDispatchRec dispatch : dispatches) {
            DispatchRequirements r = dispatch.requirements();
            if (r == null) continue;

            // capacity
            reqs.add(new QueryAttributes(
                    "capacity",
                    "=",
                    String.valueOf(r.capacity())
            ));

            if (r.cooling()) {
                reqs.add(new QueryAttributes("cooling", "=", "true"));
            }
            if (r.heating()) {
                reqs.add(new QueryAttributes("heating", "=", "true"));
            }
        }

        // 2) Filter drones by capabilities/attributes (one big AND)
        int[] attributeMatched = droneService.filterDroneAttributes(reqs);
        if (attributeMatched.length == 0) {
            return new int[0];
        }

        // 3) Further filter by date and time availability
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
                    return ld.availability(); // List<DroneAvailability>
                }
            }
        }
        return List.of(); // no availability for this drone
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
            // no constraints -> always OK
            return true;
        }


        String dispatchDay;
        if (date != null) {
            dispatchDay = date.getDayOfWeek().name();
        } else {
            dispatchDay = null;
        }

        for (DroneAvailability a : slots) {
            // Check day of week
            if (dispatchDay != null &&
                    !dispatchDay.equalsIgnoreCase(a.dayOfWeek())) {
                continue; // wrong day, try next slot
            }

            // Check time window
            if (time != null) {
                if (time.isBefore(a.from()) || time.isAfter(a.until())) {
                    continue; // time not in this slot
                }
            }

            // A slot matches this dispatch
            return true;
        }

        // No slot matched
        return false;
    }
}