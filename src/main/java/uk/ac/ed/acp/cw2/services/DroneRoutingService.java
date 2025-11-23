package uk.ac.ed.acp.cw2.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.Node;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.*;
import java.util.stream.Collectors;

import static uk.ac.ed.acp.cw2.services.DronePointInRegion.isInRegion;

@Service
public class DroneRoutingService {

    private static final Logger logger = LoggerFactory.getLogger(DroneRoutingService.class);

    private static final int MAX_PATHFINDING_ITERATIONS = 100000;
    private static final long MAX_PATHFINDING_TIME_MS = 5000;

    private final DroneAvailabilityService availabilityService;
    private final DroneQueryService droneQueryService;

    private List<RestrictedAreas> cachedRestrictedAreas = null;

    public DroneRoutingService(DroneAvailabilityService availabilityService,
                               DroneQueryService droneQueryService) {
        this.availabilityService = availabilityService;
        this.droneQueryService = droneQueryService;
    }

    public Map<String, Object> calcDeliveryPathAsGeoJson(List<MedDispatchRec> req) {
        long startTime = System.currentTimeMillis();
        logger.info("=== calcDeliveryPathAsGeoJson START (INEFFICIENT VERSION) ===");
        logger.info("Number of orders: {}", Objects.requireNonNullElse(req, Collections.emptyList()).size());

        cachedRestrictedAreas = null;

        if (req == null || req.isEmpty()) {
            logger.info("No orders, returning empty GeoJSON");
            return createEmptyGeoJson();
        }

        logger.info("Querying available drones...");
        int[] availableDrones = availabilityService.queryAvailableDrones(req);
        logger.info("Found {} available drones", availableDrones.length);

        if (availableDrones.length == 0) {
            logger.warn("No available drones found");
            return createEmptyGeoJson();
        }

        Map<Integer, LngLat> droneOrigins = droneQueryService.fetchDroneOriginLocations();
        List<DroneInfo> drones = droneQueryService.fetchDrones();
        Map<Integer, DroneCapability> capsById = drones.stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));

        cachedRestrictedAreas = droneQueryService.fetchRestrictedAreas();
        logger.info("Fetched {} restricted areas", cachedRestrictedAreas.size());

        SingleFlightResult bestResult = null;
        double bestCost = Double.POSITIVE_INFINITY;

        for (int droneId : availableDrones) {
            logger.info("Trying drone {}", droneId);

            LngLat origin = droneOrigins.get(droneId);
            DroneCapability caps = capsById.get(droneId);

            if (origin == null || caps == null) {
                logger.warn("Drone {} missing data", droneId);
                continue;
            }

            SingleFlightResult result = buildSimpleSingleFlight(origin, caps, req);

            if (result != null && result.cost < bestCost) {
                bestCost = result.cost;
                bestResult = result;
                logger.info("Drone {} feasible: cost={}, moves={}", droneId, result.cost, result.moves);
                break;
            }
        }

        if (bestResult == null) {
            logger.error("No feasible route found - returning empty GeoJSON");
            return createEmptyGeoJson();
        }

        List<List<Double>> coordinates = bestResult.fullPath.stream()
                .map(p -> List.of(p.lng(), p.lat()))
                .collect(Collectors.toList());

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "LineString");
        geoJson.put("coordinates", coordinates);

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("=== calcDeliveryPathAsGeoJson END === Total time: {}ms", totalTime);

        return geoJson;
    }

    public DeliveryPlan calcDeliveryPlan(List<MedDispatchRec> req) {
        long startTime = System.currentTimeMillis();
        logger.info("=== calcDeliveryPlan START (INEFFICIENT VERSION) ===");
        logger.info("Number of orders: {}", req == null ? 0 : req.size());

        cachedRestrictedAreas = null;

        if (req == null || req.isEmpty()) {
            logger.info("No orders, returning empty plan");
            return new DeliveryPlan(0.0, 0, List.of());
        }

        int[] availableDrones = availabilityService.queryAvailableDrones(req);
        logger.info("Found {} available drones", availableDrones.length);

        if (availableDrones.length == 0) {
            logger.warn("No available drones found");
            return new DeliveryPlan(0.0, 0, List.of());
        }

        Map<Integer, LngLat> droneOrigins = droneQueryService.fetchDroneOriginLocations();
        List<DroneInfo> drones = droneQueryService.fetchDrones();
        Map<Integer, DroneCapability> capsById = drones.stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));

        cachedRestrictedAreas = droneQueryService.fetchRestrictedAreas();
        logger.info("Fetched {} restricted areas", cachedRestrictedAreas.size());

        AllocationResult result = findOptimalAllocation(req, availableDrones, droneOrigins, capsById);

        if (result == null) {
            logger.error("Could not allocate all orders - returning empty plan");
            return new DeliveryPlan(0.0, 0, List.of());
        }

        DeliveryPlan plan = result.toPlan();
        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("=== calcDeliveryPlan END === Total: {}ms, cost={}, moves={}",
                totalTime, plan.totalCost(), plan.totalMoves());

        return plan;
    }

    private static class SingleFlightResult {
        List<MedDispatchRec> orderedDeliveries;
        List<LngLat> fullPath;
        int moves;
        double cost;

        SingleFlightResult(List<MedDispatchRec> orderedDeliveries, List<LngLat> fullPath,
                           int moves, double cost) {
            this.orderedDeliveries = orderedDeliveries;
            this.fullPath = fullPath;
            this.moves = moves;
            this.cost = cost;
        }
    }

    private static class AllocationResult {
        Map<Integer, List<FlightInfo>> droneFlights = new HashMap<>();
        double totalCost = 0.0;
        int totalMoves = 0;

        DeliveryPlan toPlan() {
            List<DronePath> dronePaths = new ArrayList<>();

            for (Map.Entry<Integer, List<FlightInfo>> entry : droneFlights.entrySet()) {
                List<DeliveryPath> deliveries = new ArrayList<>();

                for (FlightInfo flight : entry.getValue()) {
                    deliveries.addAll(flight.toDeliveryPaths());
                }

                if (!deliveries.isEmpty()) {
                    dronePaths.add(new DronePath(entry.getKey(), deliveries));
                }
            }

            return new DeliveryPlan(totalCost, totalMoves, dronePaths);
        }
    }

    private static class FlightInfo {
        List<MedDispatchRec> orders;
        List<LngLat> fullPath;
        List<Integer> hoverIndices;
        int moves;
        double cost;

        FlightInfo(List<MedDispatchRec> orders, List<LngLat> fullPath,
                   List<Integer> hoverIndices, int moves, double cost) {
            this.orders = new ArrayList<>(orders);
            this.fullPath = new ArrayList<>(fullPath);
            this.hoverIndices = new ArrayList<>(hoverIndices);
            this.moves = moves;
            this.cost = cost;
        }

        List<DeliveryPath> toDeliveryPaths() {
            List<DeliveryPath> paths = new ArrayList<>();

            for (int i = 0; i < orders.size(); i++) {
                int startIdx = (i == 0) ? 0 : hoverIndices.get(i - 1);
                int endIdx = (i == orders.size() - 1) ? fullPath.size() - 1 : hoverIndices.get(i);

                List<LngLat> segment = new ArrayList<>(fullPath.subList(startIdx, endIdx + 1));
                paths.add(new DeliveryPath(orders.get(i).id(), segment));
            }

            return paths;
        }
    }

    private SingleFlightResult buildSimpleSingleFlight(
            LngLat origin, DroneCapability caps, List<MedDispatchRec> orders) {

        logger.debug("buildSimpleSingleFlight: {} orders", orders.size());

        List<LngLat> fullPath = new ArrayList<>();
        fullPath.add(origin);
        LngLat current = origin;

        for (MedDispatchRec order : orders) {
            LngLat target = order.delivery();
            if (target == null) {
                logger.warn("Order {} has null delivery", order.id());
                return null;
            }

            logger.debug("Finding path to order {}: {} -> {}", order.id(), current, target);

            List<Node> segment = findPathForDroneWithTimeout(current, target);

            if (segment.isEmpty()) {
                logger.warn("No path found to order {}", order.id());
                return null;
            }

            for (int i = 1; i < segment.size(); i++) {
                fullPath.add(segment.get(i).getXy());
            }

            fullPath.add(target);
            current = target;
        }

        logger.debug("Finding return path to origin");
        List<Node> returnSegment = findPathForDroneWithTimeout(current, origin);
        if (returnSegment.isEmpty()) {
            logger.warn("No return path found");
            return null;
        }

        for (int i = 1; i < returnSegment.size(); i++) {
            fullPath.add(returnSegment.get(i).getXy());
        }

        int moves = countMoves(fullPath);

        if (moves > caps.maxMoves()) {
            logger.debug("Exceeds maxMoves: {} > {}", moves, caps.maxMoves());
            return null;
        }

        double cost = caps.costInitial() + caps.costFinal() + moves * caps.costPerMove();
        return new SingleFlightResult(new ArrayList<>(orders), fullPath, moves, cost);
    }

    private AllocationResult findOptimalAllocation(
            List<MedDispatchRec> allOrders,
            int[] availableDrones,
            Map<Integer, LngLat> droneOrigins,
            Map<Integer, DroneCapability> capsById) {

        logger.info("findOptimalAllocation: {} orders, {} drones", allOrders.size(), availableDrones.length);

        AllocationResult result = new AllocationResult();
        List<MedDispatchRec> remaining = new ArrayList<>(allOrders);

        int maxRounds = allOrders.size() + 5;
        int round = 0;

        while (!remaining.isEmpty() && round < maxRounds) {
            round++;
            logger.info("Round {}: {} orders remaining", round, remaining.size());
            boolean progress = false;

            for (int droneId : availableDrones) {
                if (remaining.isEmpty()) break;

                LngLat origin = droneOrigins.get(droneId);
                DroneCapability caps = capsById.get(droneId);

                if (origin == null || caps == null) {
                    logger.warn("Drone {} missing data", droneId);
                    continue;
                }

                logger.debug("Trying drone {} with {} remaining orders", droneId, remaining.size());

                FlightInfo flight = buildBestFlightSimple(origin, caps, remaining);

                if (flight != null && !flight.orders.isEmpty()) {
                    logger.info("Drone {} allocated {} orders", droneId, flight.orders.size());

                    result.droneFlights.computeIfAbsent(droneId, k -> new ArrayList<>()).add(flight);
                    result.totalCost += flight.cost;
                    result.totalMoves += flight.moves;

                    remaining.removeAll(flight.orders);
                    progress = true;
                }
            }

            if (!progress) {
                logger.error("No progress in round {}", round);
                return null;
            }
        }

        return remaining.isEmpty() ? result : null;
    }

    private FlightInfo buildBestFlightSimple(
            LngLat origin, DroneCapability caps, List<MedDispatchRec> available) {

        logger.debug("buildBestFlightSimple: {} available", available.size());

        if (available.isEmpty()) {
            return null;
        }

        List<MedDispatchRec> sorted = new ArrayList<>(available);
        sorted.sort((a, b) -> {
            double distA = a.delivery() != null ? GeometryService.distance(origin, a.delivery()) : Double.MAX_VALUE;
            double distB = b.delivery() != null ? GeometryService.distance(origin, b.delivery()) : Double.MAX_VALUE;
            return Double.compare(distA, distB);
        });

        List<MedDispatchRec> selected = new ArrayList<>();
        double totalCapacity = 0.0;

        for (MedDispatchRec order : sorted) {
            if (order.delivery() == null) continue;

            double reqCapacity = order.requirements().capacity();

            if (totalCapacity + reqCapacity > caps.capacity()) {
                continue;
            }

            List<MedDispatchRec> test = new ArrayList<>(selected);
            test.add(order);

            FlightInfo testFlight = buildFlightInfoSimple(origin, caps, test);
            if (testFlight != null) {
                selected.add(order);
                totalCapacity += reqCapacity;
            }
        }

        if (selected.isEmpty()) {
            logger.debug("No orders could be selected");
            return null;
        }

        return buildFlightInfoSimple(origin, caps, selected);
    }

    private FlightInfo buildFlightInfoSimple(
            LngLat origin, DroneCapability caps, List<MedDispatchRec> orders) {

        logger.trace("buildFlightInfoSimple: {} orders", orders.size());

        List<LngLat> fullPath = new ArrayList<>();
        List<Integer> hoverIndices = new ArrayList<>();
        fullPath.add(origin);
        LngLat current = origin;

        for (MedDispatchRec order : orders) {
            LngLat target = order.delivery();

            logger.trace("Pathfinding to order {}", order.id());
            List<Node> segment = findPathForDroneWithTimeout(current, target);

            if (segment.isEmpty()) {
                logger.trace("No path to order {}", order.id());
                return null;
            }

            for (int i = 1; i < segment.size(); i++) {
                fullPath.add(segment.get(i).getXy());
            }

            fullPath.add(target);
            fullPath.add(target);

            hoverIndices.add(fullPath.size() - 1);
            current = target;
        }

        logger.trace("Pathfinding return to origin");
        List<Node> returnSegment = findPathForDroneWithTimeout(current, origin);
        if (returnSegment.isEmpty()) {
            logger.trace("No return path");
            return null;
        }

        for (int i = 1; i < returnSegment.size(); i++) {
            fullPath.add(returnSegment.get(i).getXy());
        }

        int moves = countMoves(fullPath);
        if (moves > caps.maxMoves()) {
            logger.trace("Exceeds maxMoves: {} > {}", moves, caps.maxMoves());
            return null;
        }

        double cost = caps.costInitial() + caps.costFinal() + moves * caps.costPerMove();
        return new FlightInfo(orders, fullPath, hoverIndices, moves, cost);
    }

    private static class KeyValuePair {
        String key;
        Node value;

        KeyValuePair(String key, Node value) {
            this.key = key;
            this.value = value;
        }
    }

    private List<Node> findPathForDroneWithTimeout(LngLat origin, LngLat target) {
        long startTime = System.currentTimeMillis();

        if (inRestrictedAreaCached(target)) {
            logger.error("Target {} is in restricted area", target);
            return List.of();
        }

        if (inRestrictedAreaCached(origin)) {
            logger.error("Origin {} is in restricted area", origin);
            return List.of();
        }

        if (GeometryService.isClose(origin, target)) {
            return List.of(new Node(origin, null, target));
        }

        double straightLineDist = GeometryService.distance(origin, target);
        logger.debug("Pathfinding distance: {:.6f} degrees ({:.0f} min steps)",
                straightLineDist, straightLineDist / 0.00015);

        List<Node> open = new ArrayList<>();
        List<String> openSet = new ArrayList<>();
        List<String> closedSet = new ArrayList<>();
        List<KeyValuePair> allNodes = new ArrayList<>();

        Node startNode = new Node(origin, null, target);
        String startKey = posKey(origin);
        open.add(startNode);
        openSet.add(startKey);
        allNodes.add(new KeyValuePair(startKey, startNode));

        int iterations = 0;

        while (!open.isEmpty() && iterations < MAX_PATHFINDING_ITERATIONS) {
            iterations++;

            if (iterations % 10000 == 0) {
                Node peek = !open.isEmpty() ? open.get(0) : null;
                logger.debug("A* iteration {}: open={}, closed={}, best dist={:.6f}",
                        iterations, open.size(), closedSet.size(),
                        peek != null ? GeometryService.distance(peek.getXy(), target) : 0);
            }

            if (System.currentTimeMillis() - startTime > MAX_PATHFINDING_TIME_MS) {
                logger.error("A* timeout after {}ms, {} iterations",
                        System.currentTimeMillis() - startTime, iterations);
                return List.of();
            }

            Node current = null;
            double minFCost = Double.POSITIVE_INFINITY;
            int minIndex = -1;
            for (int i = 0; i < open.size(); i++) {
                Node node = open.get(i);
                if (node.getFCost() < minFCost) {
                    minFCost = node.getFCost();
                    current = node;
                    minIndex = i;
                }
            }

            if (minIndex >= 0) {
                open.remove(minIndex);
            }

            if (current == null) break;

            String currentKey = posKey(current.getXy());
            openSet.remove(currentKey);

            if (GeometryService.isClose(current.getXy(), target)) {
                List<Node> path = reconstructPath(current);
                logger.debug("A* SUCCESS: {}ms, {} iterations, {} steps",
                        System.currentTimeMillis() - startTime, iterations, path.size());
                return path;
            }

            closedSet.add(currentKey);

            for (Node neighbour : assignNeighbours(current, target)) {
                if (inRestrictedAreaCached(neighbour.getXy())) {
                    continue;
                }

                String neighbourKey = posKey(neighbour.getXy());

                if (closedSet.contains(neighbourKey)) {
                    continue;
                }

                Node existingNode = getNodeFromList(allNodes, neighbourKey);

                if (existingNode == null) {
                    open.add(neighbour);
                    openSet.add(neighbourKey);
                    allNodes.add(new KeyValuePair(neighbourKey, neighbour));
                } else if (neighbour.getGCost() < existingNode.getGCost()) {
                    if (openSet.contains(neighbourKey)) {
                        open.remove(existingNode);
                    }

                    existingNode.setGCost(neighbour.getGCost());
                    existingNode.setParent(current);

                    if (!openSet.contains(neighbourKey)) {
                        open.add(existingNode);
                        openSet.add(neighbourKey);
                    }
                }
            }
        }

        logger.error("A* FAILED: {}ms, {} iterations (limit: {})",
                System.currentTimeMillis() - startTime, iterations, MAX_PATHFINDING_ITERATIONS);
        return List.of();
    }

    private Node getNodeFromList(List<KeyValuePair> list, String key) {
        for (KeyValuePair pair : list) {
            if (pair.key.equals(key)) {
                return pair.value;
            }
        }
        return null;
    }

    private String posKey(LngLat pos) {
        return String.format("%.9f,%.9f", pos.lng(), pos.lat());
    }

    private List<Node> assignNeighbours(Node current, LngLat goal) {
        List<Node> neighbours = new ArrayList<>(16);
        for (uk.ac.ed.acp.cw2.data.Directions.Direction16 direction :
                uk.ac.ed.acp.cw2.data.Directions.Direction16.values()) {
            LngLat nextPos = GeometryService.stepFrom(current.getXy(), direction);
            neighbours.add(new Node(nextPos, current, goal));
        }
        return neighbours;
    }

    private boolean inRestrictedAreaCached(LngLat point) {
        if (cachedRestrictedAreas == null) {
            cachedRestrictedAreas = droneQueryService.fetchRestrictedAreas();
        }

        for (RestrictedAreas area : cachedRestrictedAreas) {
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

    private Map<String, Object> createEmptyGeoJson() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("type", "LineString");
        empty.put("coordinates", List.of());
        return empty;
    }
}