package uk.ac.ed.acp.cw2.services;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.Directions;
import uk.ac.ed.acp.cw2.data.Node;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.*;
import java.util.stream.Collectors;

import static uk.ac.ed.acp.cw2.services.DronePointInRegion.isInRegion;

@Service
public class DroneRoutingService {


    private final DroneAvailabilityService availabilityService;
    private final DroneQueryService droneQueryService;

    public DroneRoutingService(DroneAvailabilityService availabilityService,
                               DroneQueryService droneQueryService) {
        this.availabilityService = availabilityService;
        this.droneQueryService = droneQueryService;
    }

    public Map<String, Object> calcDeliveryPathAsGeoJson(List<MedDispatchRec> req) {
        int[] availableDrones = availabilityService.queryAvailableDrones(req);

        // If no drones are available, return an empty LineString (still valid GeoJSON)
        if (availableDrones.length == 0) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("type", "LineString");
            empty.put("coordinates", List.of());
            return empty;
        }

        // droneId -> starting LngLat (service point location)
        Map<Integer, LngLat> droneOrigins = droneQueryService.fetchDroneOriginLocations();

        // droneId -> capability (maxMoves, costPerMove, costInitial, costFinal, ...)
        List<DroneInfo> drones = droneQueryService.fetchDrones();
        Map<Integer, DroneCapability> capsById = drones.stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));

        double bestFlightCost = Double.POSITIVE_INFINITY;
        List<LngLat> bestFlightPath = null;

        // Try each available drone and see which can do ALL deliveries in ONE flight
        // using the MedDispatchRec list order.
        for (int droneId : availableDrones) {
            LngLat origin = droneOrigins.get(droneId);
            if (origin == null) {
                continue;
            }

            DroneCapability caps = capsById.get(droneId);
            if (caps == null) {
                continue;
            }

            // Build one continuous flight path: origin -> d1 -> d2 -> ... -> origin
            List<LngLat> flightPath = new ArrayList<>();
            flightPath.add(origin);

            LngLat current = origin;
            boolean feasible = true;

            // Visit deliveries in the order they are given
            for (MedDispatchRec order : req) {
                LngLat target = order.delivery();
                if (target == null) {
                    feasible = false;
                    break;
                }

                List<Node> legOut = findPathForDrone(current, target);
                if (legOut.isEmpty()) {
                    feasible = false;
                    break; // cannot reach this delivery
                }

                // Append outgoing leg, skipping the first node to avoid duplicate
                for (int i = 1; i < legOut.size(); i++) {
                    flightPath.add(legOut.get(i).getXy());
                }

                // Hover at delivery: duplicate last coordinate
                LngLat last = flightPath.get(flightPath.size() - 1);
                flightPath.add(last);

                current = last; // continue from here
            }

            if (!feasible) {
                continue;
            }

            // Finally, return to origin
            List<Node> legBack = findPathForDrone(current, origin);
            if (legBack.isEmpty()) {
                continue; // can't get back to service point
            }

            for (int i = 1; i < legBack.size(); i++) {
                flightPath.add(legBack.get(i).getXy());
            }

            // Check maxMoves
            int moves = countMoves(flightPath);
            if (moves > caps.maxMoves()) {
                continue;
            }

            double flightCost = caps.costInitial()
                    + caps.costFinal()
                    + moves * caps.costPerMove();

            if (flightCost < bestFlightCost) {
                bestFlightCost = flightCost;
                bestFlightPath = flightPath;
            }
        }

        if (bestFlightPath == null) {
            // According to the spec, this should not happen for this endpoint,
            // but if it does, we fail loudly so it's visible during testing.
            throw new IllegalStateException(
                    "No single-drone, single-flight route can deliver all orders");
        }

        // Build GeoJSON LineString: [ [lng, lat], ... ]
        List<List<Double>> coordinates = bestFlightPath.stream()
                .map(p -> List.of(p.lng(), p.lat()))
                .collect(Collectors.toList());

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "LineString");
        geoJson.put("coordinates", coordinates);
        return geoJson;
    }

    public DeliveryPlan calcDeliveryPlan(List<MedDispatchRec> req) {
        int[] availableDrones = availabilityService.queryAvailableDrones(req);
        if (availableDrones.length == 0) {
            // As per spec, all orders should be deliverable, but keep this safe-guard.
            return new DeliveryPlan(0.0, 0, List.of());
        }

        // droneId -> starting LngLat (service point location)
        Map<Integer, LngLat> droneOrigins = droneQueryService.fetchDroneOriginLocations();

        // droneId -> capability (maxMoves, costPerMove, costInitial, costFinal, ...)
        List<DroneInfo> drones = droneQueryService.fetchDrones();
        Map<Integer, DroneCapability> capsById = drones.stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));

        // droneId -> deliveries done by this drone (possibly several flights/strings)
        Map<Integer, List<DeliveryPath>> deliveriesByDrone = new HashMap<>();

        double totalCost = 0.0;
        int totalMoves = 0;

        // Orders we still have to allocate to some drone
        List<MedDispatchRec> remaining = new ArrayList<>(req);

        // Keep allocating until all dispatches are assigned to a flight
        while (!remaining.isEmpty()) {
            boolean progressInThisRound = false;

            for (int droneId : availableDrones) {
                if (remaining.isEmpty()) {
                    break;
                }

                LngLat origin = droneOrigins.get(droneId);
                DroneCapability caps = capsById.get(droneId);
                if (origin == null || caps == null) {
                    continue;
                }

                // Try to create the best (largest) multi-delivery flight for this drone
                FlightPlan bestFlight = buildBestFlightForDrone(origin, caps, remaining);
                if (bestFlight == null || bestFlight.deliveries().isEmpty()) {
                    continue;
                }

                progressInThisRound = true;

                // Add cost and moves for this flight
                totalMoves += bestFlight.moves();
                totalCost += bestFlight.cost();

                // Convert the flight’s global path into per-delivery segments
                List<MedDispatchRec> flightOrders = bestFlight.deliveries();
                List<LngLat> fullPath = bestFlight.fullPath();
                List<Integer> hoverIndices = bestFlight.hoverIndices();

                for (int i = 0; i < flightOrders.size(); i++) {
                    int startIndex = (i == 0) ? 0 : hoverIndices.get(i - 1);
                    int endIndex = (i == flightOrders.size() - 1)
                            ? fullPath.size() - 1
                            : hoverIndices.get(i);

                    List<LngLat> segmentPath =
                            new ArrayList<>(fullPath.subList(startIndex, endIndex + 1));

                    MedDispatchRec order = flightOrders.get(i);
                    DeliveryPath deliveryPath = new DeliveryPath(order.id(), segmentPath);

                    deliveriesByDrone
                            .computeIfAbsent(droneId, k -> new ArrayList<>())
                            .add(deliveryPath);
                }

                // Remove all deliveries of this flight from the global remaining list
                remaining.removeAll(flightOrders);
            }

            if (!progressInThisRound) {
                // We got stuck: some orders cannot be assigned to any drone within constraints
                throw new IllegalStateException(
                        "Unable to assign all orders to any available drone within maxMoves");
            }
        }

        // Build DronePath list from the map
        List<DronePath> dronePaths = new ArrayList<>();
        for (Map.Entry<Integer, List<DeliveryPath>> entry : deliveriesByDrone.entrySet()) {
            dronePaths.add(new DronePath(entry.getKey(), entry.getValue()));
        }

        return new DeliveryPlan(totalCost, totalMoves, dronePaths);
    }


    private FlightPlan buildBestFlightForDrone(LngLat origin,
                                               DroneCapability caps,
                                               List<MedDispatchRec> remaining) {

        FlightPlan bestSoFar = null;
        List<MedDispatchRec> currentSequence = new ArrayList<>();

        while (true) {
            FlightPlan bestExtensionThisRound = null;

            for (MedDispatchRec candidate : remaining) {
                if (currentSequence.contains(candidate)) {
                    continue;
                }

                List<MedDispatchRec> tentativeSeq = new ArrayList<>(currentSequence);
                tentativeSeq.add(candidate);

                FlightPlan tentativePlan = buildFlightPlanForSequence(origin, tentativeSeq, caps);
                if (tentativePlan == null) {
                    continue; // unreachable or exceeds maxMoves (or maxCost if you check it)
                }

                if (bestExtensionThisRound == null
                        || tentativePlan.deliveries().size() > bestExtensionThisRound.deliveries().size()
                        || (tentativePlan.deliveries().size() == bestExtensionThisRound.deliveries().size()
                        && tentativePlan.moves() < bestExtensionThisRound.moves())) {
                    bestExtensionThisRound = tentativePlan;
                }
            }

            if (bestExtensionThisRound == null) {
                // No further extension possible
                break;
            }

            bestSoFar = bestExtensionThisRound;
            currentSequence = bestExtensionThisRound.deliveries();
        }

        return bestSoFar;
    }


//     Build a full round-trip path for a given ordered list of deliveries:
//
//     origin -> d1 -> d1(hover) -> d2 -> d2(hover) -> ... -> dn -> dn(hover) -> origin
//
//     Returns null if any leg is unreachable or if overall moves > maxMoves.
//     Also computes the total cost for this flight.
    private FlightPlan buildFlightPlanForSequence(LngLat origin,
                                                  List<MedDispatchRec> sequence,
                                                  DroneCapability caps) {
        if (sequence.isEmpty()) {
            return null;
        }

        List<LngLat> fullPath = new ArrayList<>();
        List<Integer> hoverIndices = new ArrayList<>();

        fullPath.add(origin);
        LngLat current = origin;

        // Visit all deliveries in the given order
        for (MedDispatchRec order : sequence) {
            LngLat target = order.delivery();

            List<Node> segment = findPathForDrone(current, target);
            if (segment.isEmpty()) {
                return null; // unreachable
            }

            // Append segment (skip first node to avoid duplicating current position)
            for (int i = 1; i < segment.size(); i++) {
                fullPath.add(segment.get(i).getXy());
            }
            current = target;

            // Hover at the delivery point (two identical consecutive entries)
            fullPath.add(current);
            hoverIndices.add(fullPath.size() - 1);
        }

        // Return from last delivery to origin
        List<Node> backSegment = findPathForDrone(current, origin);
        if (backSegment.isEmpty()) {
            return null;
        }
        for (int i = 1; i < backSegment.size(); i++) {
            fullPath.add(backSegment.get(i).getXy());
        }

        int moves = countMoves(fullPath);
        if (moves > caps.maxMoves()) {
            return null;
        }

        double flightCost = caps.costInitial()
                + caps.costFinal()
                + moves * caps.costPerMove();


        // Make defensive copies for safety
        return new FlightPlan(
                new ArrayList<>(sequence),
                new ArrayList<>(fullPath),
                new ArrayList<>(hoverIndices),
                moves,
                flightCost
        );
    }

    // Small internal record to describe a single flight (possibly multiple deliveries).
    private record FlightPlan(
            List<MedDispatchRec> deliveries,
            List<LngLat> fullPath,
            List<Integer> hoverIndices,
            int moves,
            double cost
    ) {
    }

    // ====================== A* search and helpers ======================

    // A* search for a single origin -> target.
    private List<Node> findPathForDrone(LngLat origin, LngLat target) {
        List<Node> open = new ArrayList<>();   // frontier
        List<Node> closed = new ArrayList<>(); // explored

        Node start = new Node(origin, null, target);
        open.add(start);

        while (!open.isEmpty()) {
            // Node with lowest f = g + h
            Node current = Collections.min(open, Comparator.comparingDouble(Node::getFCost));

            // Goal check (use isClose rather than == for doubles)
            if (GeometryService.isClose(current.getXy(), target)) {
                return reconstructPath(current);
            }

            open.remove(current);
            closed.add(current);

            List<Node> neighbours = assignNeighbours(current, target);

            for (Node neighbour : neighbours) {
                // skip if neighbour is in a restricted area
                if (inRestrictedArea(neighbour.getXy())) {
                    continue;
                }

                // If there's an equivalent node in CLOSED with a better gCost, skip
                Node closedNode = findNodeWithSamePosition(closed, neighbour.getXy());
                if (closedNode != null && closedNode.getGCost() <= neighbour.getGCost()) {
                    continue;
                }

                // Check if neighbour is in OPEN already
                Node openNode = findNodeWithSamePosition(open, neighbour.getXy());
                if (openNode == null) {
                    // new node -> add to open set
                    open.add(neighbour);
                } else if (neighbour.getGCost() < openNode.getGCost()) {
                    // better path to an existing open node: update it
                    openNode.setGCost(neighbour.getGCost());
                    openNode.setParent(current);
                    // hCost already correct (depends only on position->goal)
                }
            }
        }

        // No path found
        return List.of();
    }

    // Create 16 neighbour nodes around current, 1 step in each direction.
    private List<Node> assignNeighbours(Node current, LngLat goal) {
        List<Node> neighbours = new ArrayList<>();

        for (Directions.Direction16 direction : Directions.Direction16.values()) {
            LngLat nextPos = GeometryService.stepFrom(current.getXy(), direction);
            Node neighbour = new Node(nextPos, current, goal);
            neighbours.add(neighbour);
        }

        return neighbours;
    }

    // True if point lies in any restricted polygon.
    private boolean inRestrictedArea(LngLat point) {
        List<RestrictedAreas> areas = droneQueryService.fetchRestrictedAreas();
        for (RestrictedAreas area : areas) {
            if (isInRegion(point, area.vertices())) {
                return true;
            }
        }
        return false;
    }

    // Reconstruct path from goal node back to start using parent links.
    private List<Node> reconstructPath(Node goalNode) {
        List<Node> path = new ArrayList<>();
        Node current = goalNode;
        while (current != null) {
            path.add(current);
            current = current.getParent();
        }
        Collections.reverse(path);
        return path;
    }

    // Linear search for a node with the same position (LngLat).
    private Node findNodeWithSamePosition(List<Node> nodes, LngLat position) {
        for (Node node : nodes) {
            if (node.getXy().equals(position)) {
                return node;
            }
        }
        return null;
    }

    // Count moves as transitions between different coordinates.
    // The hover (duplicate LngLat) does not add to the move count.
    private int countMoves(List<LngLat> flightPath) {
        int moves = 0;
        for (int i = 1; i < flightPath.size(); i++) {
            if (!flightPath.get(i).equals(flightPath.get(i - 1))) {
                moves++;
            }
        }
        return moves;
    }
}