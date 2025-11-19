package uk.ac.ed.acp.cw2.services;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.Node;
import uk.ac.ed.acp.cw2.dto.*;
import java.util.*;

@Service
public class DroneRoutingService {

    private final DroneAvailabilityService availabilityService;
    private final DroneQueryService droneQueryService;
    public DroneRoutingService(DroneAvailabilityService availabilityService, DroneQueryService droneQueryService) {
        this.availabilityService = availabilityService;
        this.droneQueryService = droneQueryService;
    }

    // Function should receive a me
    // A* Path finding algorithm
    // f cost of each node will differ based on the cost of one move for a drone
    public List<Node> pathFinder(List<MedDispatchRec> req) {
        int[] availableDrones = availabilityService.queryAvailableDrones(req);
        if (availableDrones.length == 0) {
            return List.of(); // or throw, depending on spec
        }

        int[] cost; // cost of path for each drone

        //open.add();
        for (int drone: availableDrones) {
            List<Node> open = new ArrayList<>(); // set of nodes to be evaluated
            List<Node> closed = new ArrayList<>(); // set of nodes already evaluated
            Node start = new Node(droneOrigin(drone));
            open.add(start);
        }

        return null;
    }

    public LngLat droneOrigin(int suitableDrone) {
        // fetch the "Drones for Service points" json
        List<DronesForServicePoints> servicePoints = droneQueryService.fetchDroneAvailability();
        // Goes through each service point
        for (DronesForServicePoints servicePoint: servicePoints) {
            // service point id
            int servicePointId = servicePoint.servicePointId();
            // list of drones for the respective service point
            List<ListDrones> drones = servicePoint.drones();
            // Check if the drone were looking up is stationed in the service point
            // If stationed in the service point then return the service points LngLat
            for (ListDrones drone: drones) {
                if (drone.id() == suitableDrone) {
                    return fetchServicePointLngLat(servicePointId);
                }
            }
        }
        return null;
    }

    public LngLat fetchServicePointLngLat(int servicePointId) {
        List<ServicePoints> servicePoints = droneQueryService.fetchServicePoints();
        for (ServicePoints service : servicePoints) {
            if (servicePointId == service.id()) {
                return service.location();
            }
        }
        return null;
    }
}