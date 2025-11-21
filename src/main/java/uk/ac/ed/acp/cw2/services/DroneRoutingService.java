package uk.ac.ed.acp.cw2.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.Directions;
import uk.ac.ed.acp.cw2.data.Node;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.*;
import java.util.stream.Collectors;

import static uk.ac.ed.acp.cw2.services.DronePointInRegion.isInRegion;

@Service
public class DroneRoutingService {

    private static final Logger log = LoggerFactory.getLogger(DroneRoutingService.class);

    private final DroneAvailabilityService availabilityService;
    private final DroneQueryService droneQueryService;

    // Cache for paths to avoid recalculation
    private final Map<String, List<Node>> pathCache = new HashMap<>();

    // Cache restricted areas to avoid repeated API calls
    private List<RestrictedAreas> restrictedAreasCache = null;

    public DroneRoutingService(DroneAvailabilityService availabilityService,
                               DroneQueryService droneQueryService) {
        this.availabilityService = availabilityService;
        this.droneQueryService = droneQueryService;
    }

    public Map<String, Object> calcDeliveryPathAsGeoJson(List<MedDispatchRec> req) {
        long startTime = System.currentTimeMillis();
        log.info("=== calcDeliveryPathAsGeoJson START ===");
        log.info("Processing {} deliveries", req.size());

        pathCache.clear();
        restrictedAreasCache = null;

        long t1 = System.currentTimeMillis();
        int[] availableDrones = availabilityService.queryAvailableDrones(req);
        log.info("Available drones query took {}ms, found {} drones",
                System.currentTimeMillis() - t1, availableDrones.length);

        if (availableDrones.length == 0) {
            log.warn("No available drones found");
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("type", "LineString");
            empty.put("coordinates", List.of());
            return empty;
        }

        long t2 = System.currentTimeMillis();
        Map<Integer, LngLat> droneOrigins = droneQueryService.fetchDroneOriginLocations();
        List<DroneInfo> drones = droneQueryService.fetchDrones();
        Map<Integer, DroneCapability> capsById = drones.stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));
        log.info("Fetched drone data in {}ms", System.currentTimeMillis() - t2);

        // Pre-cache restricted areas
        long t3 = System.currentTimeMillis();
        restrictedAreasCache = droneQueryService.fetchRestrictedAreas();
        log.info("Fetched {} restricted areas in {}ms",
                restrictedAreasCache.size(), System.currentTimeMillis() - t3);

        double bestFlightCost = Double.POSITIVE_INFINITY;
        List<LngLat> bestFlightPath = null;
        int dronesTried = 0;

        for (int droneId : availableDrones) {
            dronesTried++;
            long droneStart = System.currentTimeMillis();
            log.debug("Trying drone {} ({}/{})", droneId, dronesTried, availableDrones.length);

            LngLat origin = droneOrigins.get(droneId);
            if (origin == null) {
                log.warn("No origin found for drone {}", droneId);
                continue;
            }

            DroneCapability caps = capsById.get(droneId);
            if (caps == null) {
                log.warn("No capability found for drone {}", droneId);
                continue;
            }

            List<LngLat> flightPath = new ArrayList<>();
            flightPath.add(origin);

            LngLat current = origin;
            boolean feasible = true;

            for (int i = 0; i < req.size(); i++) {
                MedDispatchRec order = req.get(i);
                LngLat target = order.delivery();
                if (target == null) {
                    log.warn("Delivery {} has no target location", order.id());
                    feasible = false;
                    break;
                }

                long pathStart = System.currentTimeMillis();
                List<Node> legOut = findPathForDrone(current, target);
                long pathTime = System.currentTimeMillis() - pathStart;

                if (legOut.isEmpty()) {
                    log.debug("No path found from {} to {} (took {}ms)", current, target, pathTime);
                    feasible = false;
                    break;
                }

                log.debug("Path {}->{} found in {}ms ({} nodes)",
                        i, i+1, pathTime, legOut.size());

                for (int j = 1; j < legOut.size(); j++) {
                    flightPath.add(legOut.get(j).getXy());
                }

                // Hover at delivery
                LngLat last = flightPath.get(flightPath.size() - 1);
                flightPath.add(last);

                current = last;
            }

            if (!feasible) {
                log.debug("Drone {} not feasible for route", droneId);
                continue;
            }

            // Return to origin
            long returnStart = System.currentTimeMillis();
            List<Node> legBack = findPathForDrone(current, origin);
            log.debug("Return path took {}ms", System.currentTimeMillis() - returnStart);

            if (legBack.isEmpty()) {
                log.debug("Cannot return to origin for drone {}", droneId);
                continue;
            }

            for (int i = 1; i < legBack.size(); i++) {
                flightPath.add(legBack.get(i).getXy());
            }

            int moves = countMoves(flightPath);
            if (moves > caps.maxMoves()) {
                log.debug("Drone {} exceeds maxMoves: {} > {}", droneId, moves, caps.maxMoves());
                continue;
            }

            double flightCost = caps.costInitial() + caps.costFinal() + moves * caps.costPerMove();

            // Check maxCost constraint
            double costPerDelivery = flightCost / req.size();
            boolean exceedsMaxCost = false;
            for (MedDispatchRec order : req) {
                if (order.requirements() != null && order.requirements().maxCost() != null) {
                    if (costPerDelivery > order.requirements().maxCost()) {
                        log.debug("Drone {} exceeds maxCost for order {}: {} > {}",
                                droneId, order.id(), costPerDelivery, order.requirements().maxCost());
                        exceedsMaxCost = true;
                        break;
                    }
                }
            }
            if (exceedsMaxCost) continue;

            log.debug("Drone {} completed in {}ms: cost={}, moves={}",
                    droneId, System.currentTimeMillis() - droneStart, flightCost, moves);

            if (flightCost < bestFlightCost) {
                bestFlightCost = flightCost;
                bestFlightPath = flightPath;
                log.info("New best: drone {} with cost {} and {} moves", droneId, flightCost, moves);
            }
        }

        if (bestFlightPath == null) {
            log.error("No single-drone route found after trying {} drones", dronesTried);
            throw new IllegalStateException(
                    "No single-drone, single-flight route can deliver all orders");
        }

        List<List<Double>> coordinates = bestFlightPath.stream()
                .map(p -> List.of(p.lng(), p.lat()))
                .collect(Collectors.toList());

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "LineString");
        geoJson.put("coordinates", coordinates);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("=== calcDeliveryPathAsGeoJson COMPLETE in {}ms ===", totalTime);
        log.info("Best cost: {}, total coordinates: {}", bestFlightCost, coordinates.size());

        return geoJson;
    }

    public DeliveryPlan calcDeliveryPlan(List<MedDispatchRec> req) {
        long startTime = System.currentTimeMillis();
        log.info("=== calcDeliveryPlan START ===");
        log.info("Processing {} deliveries", req.size());

        pathCache.clear();
        restrictedAreasCache = null;

        long t1 = System.currentTimeMillis();
        int[] availableDrones = availabilityService.queryAvailableDrones(req);
        log.info("Available drones query took {}ms, found {} drones",
                System.currentTimeMillis() - t1, availableDrones.length);

        if (availableDrones.length == 0) {
            log.warn("No available drones found");
            return new DeliveryPlan(0.0, 0, List.of());
        }

        long t2 = System.currentTimeMillis();
        Map<Integer, LngLat> droneOrigins = droneQueryService.fetchDroneOriginLocations();
        List<DroneInfo> drones = droneQueryService.fetchDrones();
        Map<Integer, DroneCapability> capsById = drones.stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));
        log.info("Fetched drone data in {}ms", System.currentTimeMillis() - t2);

        // Pre-cache restricted areas
        long t3 = System.currentTimeMillis();
        restrictedAreasCache = droneQueryService.fetchRestrictedAreas();
        log.info("Fetched {} restricted areas in {}ms",
                restrictedAreasCache.size(), System.currentTimeMillis() - t3);

        Map<Integer, List<DeliveryPath>> deliveriesByDrone = new HashMap<>();

        double totalCost = 0.0;
        int totalMoves = 0;

        List<MedDispatchRec> remaining = new ArrayList<>(req);
        int maxIterations = 100; // Reduced safety limit
        int iterations = 0;

        while (!remaining.isEmpty() && iterations++ < maxIterations) {
            log.info("Iteration {}: {} deliveries remaining", iterations, remaining.size());
            boolean progressInThisRound = false;

            for (int droneId : availableDrones) {
                if (remaining.isEmpty()) break;

                LngLat origin = droneOrigins.get(droneId);
                DroneCapability caps = capsById.get(droneId);
                if (origin == null || caps == null) continue;

                long flightStart = System.currentTimeMillis();
                FlightPlan bestFlight = buildGreedyFlight(origin, caps, remaining);
                long flightTime = System.currentTimeMillis() - flightStart;

                if (bestFlight == null || bestFlight.deliveries().isEmpty()) {
                    log.debug("Drone {} cannot handle any remaining deliveries", droneId);
                    continue;
                }

                log.info("Drone {} flight planned in {}ms: {} deliveries, {} moves, cost={}",
                        droneId, flightTime, bestFlight.deliveries().size(),
                        bestFlight.moves(), bestFlight.cost());

                progressInThisRound = true;

                totalMoves += bestFlight.moves();
                totalCost += bestFlight.cost();

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

                remaining.removeAll(flightOrders);
                log.info("Removed {} deliveries, {} remaining", flightOrders.size(), remaining.size());
            }

            if (!progressInThisRound) {
                log.error("No progress in iteration {}, {} deliveries stuck", iterations, remaining.size());
                throw new IllegalStateException(
                        "Unable to assign all orders to any available drone within maxMoves");
            }
        }

        List<DronePath> dronePaths = new ArrayList<>();
        for (Map.Entry<Integer, List<DeliveryPath>> entry : deliveriesByDrone.entrySet()) {
            dronePaths.add(new DronePath(entry.getKey(), entry.getValue()));
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("=== calcDeliveryPlan COMPLETE in {}ms ===", totalTime);
        log.info("Total cost: {}, total moves: {}, drones used: {}",
                totalCost, totalMoves, dronePaths.size());

        return new DeliveryPlan(totalCost, totalMoves, dronePaths);
    }

    /**
     * Simplified greedy approach - builds flight by adding deliveries one at a time
     */
    private FlightPlan buildGreedyFlight(LngLat origin,
                                         DroneCapability caps,
                                         List<MedDispatchRec> remaining) {
        log.debug("Building greedy flight from {} with {} remaining deliveries", origin, remaining.size());

        List<MedDispatchRec> currentSequence = new ArrayList<>();
        int checksPerformed = 0;

        // Try adding deliveries one by one (greedy)
        for (MedDispatchRec candidate : remaining) {
            checksPerformed++;
            List<MedDispatchRec> testSequence = new ArrayList<>(currentSequence);
            testSequence.add(candidate);

            FlightPlan testPlan = buildFlightPlanForSequence(origin, testSequence, caps);
            if (testPlan != null) {
                currentSequence = testSequence;
                log.debug("Added delivery {} to sequence ({} total)", candidate.id(), currentSequence.size());
            } else {
                log.debug("Cannot add delivery {} (would exceed constraints)", candidate.id());
            }
        }

        log.debug("Greedy flight built: {} deliveries after {} checks", currentSequence.size(), checksPerformed);
        return buildFlightPlanForSequence(origin, currentSequence, caps);
    }

    /**
     * Build a full round-trip path with cost validation per delivery
     */
    private FlightPlan buildFlightPlanForSequence(LngLat origin,
                                                  List<MedDispatchRec> sequence,
                                                  DroneCapability caps) {
        if (sequence.isEmpty()) return null;

        List<LngLat> fullPath = new ArrayList<>();
        List<Integer> hoverIndices = new ArrayList<>();

        fullPath.add(origin);
        LngLat current = origin;

        for (MedDispatchRec order : sequence) {
            LngLat target = order.delivery();
            if (target == null) return null;

            List<Node> segment = findPathForDrone(current, target);
            if (segment.isEmpty()) return null;

            for (int i = 1; i < segment.size(); i++) {
                fullPath.add(segment.get(i).getXy());
            }
            current = target;

            fullPath.add(current);
            hoverIndices.add(fullPath.size() - 1);
        }

        List<Node> backSegment = findPathForDrone(current, origin);
        if (backSegment.isEmpty()) return null;

        for (int i = 1; i < backSegment.size(); i++) {
            fullPath.add(backSegment.get(i).getXy());
        }

        int moves = countMoves(fullPath);
        if (moves > caps.maxMoves()) return null;

        double flightCost = caps.costInitial() + caps.costFinal() + moves * caps.costPerMove();

        // Validate maxCost per delivery (pro-rata)
        double costPerDelivery = flightCost / sequence.size();
        for (MedDispatchRec order : sequence) {
            if (order.requirements() != null && order.requirements().maxCost() != null) {
                if (costPerDelivery > order.requirements().maxCost()) {
                    return null;
                }
            }
        }

        return new FlightPlan(
                new ArrayList<>(sequence),
                new ArrayList<>(fullPath),
                new ArrayList<>(hoverIndices),
                moves,
                flightCost
        );
    }

    private record FlightPlan(
            List<MedDispatchRec> deliveries,
            List<LngLat> fullPath,
            List<Integer> hoverIndices,
            int moves,
            double cost
    ) {}

    // ====================== OPTIMIZED A* search ======================

    private List<Node> findPathForDrone(LngLat origin, LngLat target) {
        // Check cache first
        String cacheKey = positionKey(origin) + "->" + positionKey(target);
        if (pathCache.containsKey(cacheKey)) {
            log.trace("Cache hit for {}", cacheKey);
            return new ArrayList<>(pathCache.get(cacheKey));
        }

        long searchStart = System.currentTimeMillis();

        // Use HashMap for O(1) lookups instead of List
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(Node::getFCost));
        Map<String, Node> openMap = new HashMap<>();
        Map<String, Node> closedMap = new HashMap<>();

        Node start = new Node(origin, null, target);
        openSet.add(start);
        openMap.put(positionKey(origin), start);

        int maxIterations = 5000; // Further reduced for faster failure
        int iterations = 0;

        while (!openSet.isEmpty() && iterations++ < maxIterations) {
            Node current = openSet.poll();
            String currentKey = positionKey(current.getXy());
            openMap.remove(currentKey);

            if (GeometryService.isClose(current.getXy(), target)) {
                List<Node> path = reconstructPath(current);
                pathCache.put(cacheKey, path);
                long searchTime = System.currentTimeMillis() - searchStart;
                log.trace("Path found in {}ms after {} iterations", searchTime, iterations);
                return path;
            }

            closedMap.put(currentKey, current);

            for (Directions.Direction16 direction : Directions.Direction16.values()) {
                LngLat nextPos = GeometryService.stepFrom(current.getXy(), direction);

                if (inRestrictedArea(nextPos)) continue;

                String nextKey = positionKey(nextPos);

                Node closedNode = closedMap.get(nextKey);
                if (closedNode != null && closedNode.getGCost() <= current.getGCost() + 1) {
                    continue;
                }

                Node neighbour = new Node(nextPos, current, target);

                Node openNode = openMap.get(nextKey);
                if (openNode == null) {
                    openSet.add(neighbour);
                    openMap.put(nextKey, neighbour);
                } else if (neighbour.getGCost() < openNode.getGCost()) {
                    openSet.remove(openNode);
                    openNode.setGCost(neighbour.getGCost());
                    openNode.setParent(current);
                    openSet.add(openNode);
                }
            }
        }

        long searchTime = System.currentTimeMillis() - searchStart;
        log.warn("No path found from {} to {} after {}ms and {} iterations",
                origin, target, searchTime, iterations);
        return List.of();
    }

    private String positionKey(LngLat pos) {
        return String.format("%.6f,%.6f", pos.lng(), pos.lat());
    }

    private boolean inRestrictedArea(LngLat point) {
        // Use cached restricted areas
        if (restrictedAreasCache == null) {
            restrictedAreasCache = droneQueryService.fetchRestrictedAreas();
        }

        for (RestrictedAreas area : restrictedAreasCache) {
            if (isInRegion(point, area.vertices())) {
                return true;
            }
        }
        return false;
    }

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