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

    // Build a DeliveryPlan for the given MedDispatchRecs.
    // Currently: one delivery per flight:
    // ServicePoint -> delivery -> ServicePoint
    // respecting maxMoves and using costInitial / costFinal / costPerMove.
    public DeliveryPlan calcDeliveryPlan(List<MedDispatchRec> req) {
        int[] availableDrones = availabilityService.queryAvailableDrones(req);
        if (availableDrones.length == 0) {
            return new DeliveryPlan(0.0, 0, List.of());
        }

        // droneId -> starting LngLat (service point location)
        Map<Integer, LngLat> droneOrigins = droneQueryService.fetchDroneOriginLocations();

        // droneId -> capability (maxMoves, costPerMove, costInitial, costFinal, ...)
        List<DroneInfo> drones = droneQueryService.fetchDrones();
        Map<Integer, DroneCapability> capsById = drones.stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));

        // droneId -> deliveries done by this drone
        Map<Integer, List<DeliveryPath>> deliveriesByDrone = new HashMap<>();

        double totalCost = 0.0;
        int totalMoves = 0;

        for (MedDispatchRec order : req) {
            LngLat target = order.delivery();

            int bestDroneId = -1;
            List<Node> bestOutPath = List.of();
            List<Node> bestBackPath = List.of();
            int bestMoves = Integer.MAX_VALUE;
            double bestFlightCost = Double.POSITIVE_INFINITY;

            // Find best drone (cheapest valid round trip SP -> delivery -> SP)
            for (int droneId : availableDrones) {
                LngLat origin = droneOrigins.get(droneId);
                if (origin == null) {
                    continue; // no origin for this drone
                }

                DroneCapability caps = capsById.get(droneId);
                if (caps == null) {
                    continue; // no capability info → skip
                }

                List<Node> outPath = findPathForDrone(origin, target);
                if (outPath.isEmpty()) {
                    continue; // unreachable
                }

                List<Node> backPath = findPathForDrone(target, origin);
                if (backPath.isEmpty()) {
                    continue; // can't get back to service point
                }

                // Build full round trip path to evaluate moves and cost
                List<LngLat> candidateFlightPath = buildRoundTripFlightPath(outPath, backPath);
                int moves = countMoves(candidateFlightPath);

                // Respect maxMoves (battery) per flight
                if (moves > caps.maxMoves()) {
                    continue;
                }

                // Monetary cost according to spec:
                // costInitial + costFinal + moves * costPerMove
                double flightCost = caps.costInitial()
                        + caps.costFinal()
                        + moves * caps.costPerMove();

                if (flightCost < bestFlightCost) {
                    bestFlightCost = flightCost;
                    bestDroneId = droneId;
                    bestOutPath = outPath;
                    bestBackPath = backPath;
                    bestMoves = moves;
                }
            }

            if (bestDroneId == -1) {
                // Spec says all orders can and must be delivered, so if this ever happens
                // something in data or logic is badly wrong.
                throw new IllegalStateException(
                        "No available drone can deliver order " + order.id());
            }

            // Build final flightPath for the chosen drone
            List<LngLat> flightPath = buildRoundTripFlightPath(bestOutPath, bestBackPath);

            totalCost += bestFlightCost;
            totalMoves += bestMoves;

            deliveriesByDrone
                    .computeIfAbsent(bestDroneId, k -> new ArrayList<>())
                    .add(new DeliveryPath(order.id(), flightPath));
        }

        // Build DronePath list from the map
        List<DronePath> dronePaths = new ArrayList<>();
        for (Map.Entry<Integer, List<DeliveryPath>> entry : deliveriesByDrone.entrySet()) {
            dronePaths.add(new DronePath(entry.getKey(), entry.getValue()));
        }

        return new DeliveryPlan(totalCost, totalMoves, dronePaths);
    }

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


    // Build a flightPath for:
    //   ServicePoint -> delivery (outPath)
    //   hover at delivery (duplicate point)
    //   delivery -> ServicePoint (backPath)

    private List<LngLat> buildRoundTripFlightPath(List<Node> outPath, List<Node> backPath) {
        List<LngLat> flightPath = new ArrayList<>();

        if (outPath.isEmpty() || backPath.isEmpty()) {
            return flightPath;
        }

        // add outbound path
        for (Node n : outPath) {
            flightPath.add(n.getXy());
        }

        // hover (duplicate last point)
        LngLat deliveryPos = outPath.get(outPath.size() - 1).getXy();
        flightPath.add(deliveryPos);

        // add return path, skipping first node (it's the delivery position again)
        for (int i = 1; i < backPath.size(); i++) {
            flightPath.add(backPath.get(i).getXy());
        }

        return flightPath;
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