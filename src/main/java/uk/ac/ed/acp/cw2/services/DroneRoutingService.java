package uk.ac.ed.acp.cw2.services;

import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.Node;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.*;
import java.util.stream.Collectors;

import static uk.ac.ed.acp.cw2.services.DronePointInRegion.isInRegion;

/**
 * Service responsible for calculating optimal drone delivery routes.
 * Uses A* pathfinding algorithm with constraint checking for restricted areas,
 * drone capacity, and move limits.
 */
@Service
public class DroneRoutingService {

    private static final Logger logger = LoggerFactory.getLogger(DroneRoutingService.class);

    // Pathfinding constraints
    private static final int MAX_PATHFINDING_ITERATIONS = 100_000;
    private static final long MAX_PATHFINDING_TIME_MS = 5_000;
    private static final int PATHFINDING_LOG_INTERVAL = 10_000;

    // Allocation constraints
    private static final int MAX_ALLOCATION_ROUNDS = 100;

    private final DroneAvailabilityService availabilityService;
    private final DroneQueryService droneQueryService;

    // Thread-safe caching using ThreadLocal for concurrent requests
    private final ThreadLocal<List<RestrictedAreas>> restrictedAreasCache = new ThreadLocal<>();

    public DroneRoutingService(DroneAvailabilityService availabilityService,
                               DroneQueryService droneQueryService) {
        this.availabilityService = availabilityService;
        this.droneQueryService = droneQueryService;
    }

    /**
     * Calculate delivery path as GeoJSON for visualization.
     * Returns a LineString GeoJSON feature representing the complete flight path.
     */
    public Map<String, Object> calcDeliveryPathAsGeoJson(List<MedDispatchRec> orders) {
        long startTime = System.currentTimeMillis();
        logger.info("=== calcDeliveryPathAsGeoJson START ===");
        logger.info("Number of orders: {}", orders == null ? 0 : orders.size());

        try {
            clearCache();

            if (orders == null || orders.isEmpty()) {
                logger.info("No orders, returning empty GeoJSON");
                return createEmptyGeoJson();
            }

            logger.info("Querying available drones...");
            int[] availableDrones = availabilityService.queryAvailableDrones(orders);
            logger.info("Found {} available drones", availableDrones.length);

            if (availableDrones.length == 0) {
                logger.warn("No available drones found");
                return createEmptyGeoJson();
            }

            DroneContext context = buildDroneContext(availableDrones);
            SingleFlightResult bestResult = findBestSingleDroneFlight(orders, availableDrones, context);

            if (bestResult == null) {
                logger.error("No feasible route found - returning empty GeoJSON");
                return createEmptyGeoJson();
            }

            Map<String, Object> geoJson = convertToGeoJson(bestResult.fullPath);

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("=== calcDeliveryPathAsGeoJson END === Total time: {}ms", totalTime);

            return geoJson;
        } finally {
            clearCache();
        }
    }

    /**
     * Calculate optimal delivery plan allocating orders across multiple drones.
     * Returns a complete plan with cost breakdown and paths for each drone.
     */
    public DeliveryPlan calcDeliveryPlan(List<MedDispatchRec> orders) {
        long startTime = System.currentTimeMillis();
        logger.info("=== calcDeliveryPlan START ===");
        logger.info("Number of orders: {}", orders == null ? 0 : orders.size());

        try {
            clearCache();

            if (orders == null || orders.isEmpty()) {
                logger.info("No orders, returning empty plan");
                return new DeliveryPlan(0.0, 0, List.of());
            }

            int[] availableDrones = availabilityService.queryAvailableDrones(orders);
            logger.info("Found {} available drones", availableDrones.length);

            if (availableDrones.length == 0) {
                logger.warn("No available drones found");
                return new DeliveryPlan(0.0, 0, List.of());
            }

            DroneContext context = buildDroneContext(availableDrones);
            AllocationResult result = findOptimalAllocation(orders, availableDrones, context);

            if (result == null) {
                logger.error("Could not allocate all orders - returning empty plan");
                return new DeliveryPlan(0.0, 0, List.of());
            }

            DeliveryPlan plan = result.toPlan();
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("=== calcDeliveryPlan END === Total: {}ms, cost={}, moves={}",
                    totalTime, plan.totalCost(), plan.totalMoves());

            return plan;
        } finally {
            clearCache();
        }
    }

    // ==================== Helper Classes ====================

    /**
     * Encapsulates drone fleet information for efficient lookup.
     */
    private static class DroneContext {
        final Map<Integer, LngLat> origins;
        final Map<Integer, DroneCapability> capabilities;

        DroneContext(Map<Integer, LngLat> origins, Map<Integer, DroneCapability> capabilities) {
            this.origins = origins;
            this.capabilities = capabilities;
        }

        LngLat getOrigin(int droneId) {
            return origins.get(droneId);
        }

        DroneCapability getCapability(int droneId) {
            return capabilities.get(droneId);
        }

        boolean hasDroneData(int droneId) {
            return origins.containsKey(droneId) && capabilities.containsKey(droneId);
        }
    }

    /**
     * Result of a single drone flight covering one or more orders.
     */
    private static class SingleFlightResult {
        final List<MedDispatchRec> orderedDeliveries;
        final List<LngLat> fullPath;
        final int moves;
        final double cost;

        SingleFlightResult(List<MedDispatchRec> orderedDeliveries, List<LngLat> fullPath,
                           int moves, double cost) {
            this.orderedDeliveries = new ArrayList<>(orderedDeliveries);
            this.fullPath = new ArrayList<>(fullPath);
            this.moves = moves;
            this.cost = cost;
        }
    }

    /**
     * Complete allocation of all orders to drones with cost tracking.
     */
    private static class AllocationResult {
        final Map<Integer, List<FlightInfo>> droneFlights = new HashMap<>();
        double totalCost = 0.0;
        int totalMoves = 0;

        void addFlight(int droneId, FlightInfo flight) {
            droneFlights.computeIfAbsent(droneId, k -> new ArrayList<>()).add(flight);
            totalCost += flight.cost;
            totalMoves += flight.moves;
        }

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

    /**
     * Detailed flight information including hover points for delivery.
     */
    private static class FlightInfo {
        final List<MedDispatchRec> orders;
        final List<LngLat> fullPath;
        final List<Integer> hoverIndices;
        final int moves;
        final double cost;

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

    /**
     * Efficient key-value storage for A* pathfinding nodes.
     */
    private static class NodeMap {
        private final Map<String, Node> nodes = new HashMap<>();

        void put(String key, Node node) {
            nodes.put(key, node);
        }

        Node get(String key) {
            return nodes.get(key);
        }

        boolean contains(String key) {
            return nodes.containsKey(key);
        }
    }

    // ==================== Context Building ====================

    /**
     * Build context with drone locations and capabilities.
     */
    private DroneContext buildDroneContext(int[] droneIds) {
        Map<Integer, LngLat> origins = droneQueryService.fetchDroneOriginLocations();

        Map<Integer, DroneCapability> capabilities = droneQueryService.fetchDrones().stream()
                .collect(Collectors.toMap(DroneInfo::id, DroneInfo::capability));

        // Initialize restricted areas cache
        getRestrictedAreas();

        return new DroneContext(origins, capabilities);
    }

    // ==================== Single Flight Planning ====================

    /**
     * Find the best single drone that can complete all orders.
     */
    private SingleFlightResult findBestSingleDroneFlight(
            List<MedDispatchRec> orders,
            int[] availableDrones,
            DroneContext context) {

        SingleFlightResult bestResult = null;
        double bestCost = Double.POSITIVE_INFINITY;

        for (int droneId : availableDrones) {
            logger.info("Trying drone {}", droneId);

            if (!context.hasDroneData(droneId)) {
                logger.warn("Drone {} missing data", droneId);
                continue;
            }

            LngLat origin = context.getOrigin(droneId);
            DroneCapability caps = context.getCapability(droneId);

            SingleFlightResult result = buildSimpleSingleFlight(origin, caps, orders);

            if (result != null && result.cost < bestCost) {
                bestCost = result.cost;
                bestResult = result;
                logger.info("Drone {} feasible: cost={}, moves={}",
                        droneId, result.cost, result.moves);
                break; // First feasible is good enough for single flight
            }
        }

        return bestResult;
    }

    /**
     * Build a flight path visiting all orders in sequence.
     */
    private SingleFlightResult buildSimpleSingleFlight(
            LngLat origin,
            DroneCapability caps,
            List<MedDispatchRec> orders) {

        logger.debug("buildSimpleSingleFlight: {} orders", orders.size());

        List<LngLat> fullPath = new ArrayList<>();
        fullPath.add(origin);
        LngLat current = origin;

        // Visit each delivery location
        for (MedDispatchRec order : orders) {
            LngLat target = order.delivery();
            if (target == null) {
                logger.warn("Order {} has null delivery location", order.id());
                return null;
            }

            logger.debug("Finding path to order {}: {} -> {}", order.id(), current, target);

            List<Node> segment = findPathWithTimeout(current, target);
            if (segment.isEmpty()) {
                logger.warn("No path found to order {}", order.id());
                return null;
            }

            // Add segment (skip first point as it's current position)
            segment.stream().skip(1).forEach(node -> fullPath.add(node.getXy()));

            fullPath.add(target);
            current = target;
        }

        // Return to origin
        logger.debug("Finding return path to origin");
        List<Node> returnSegment = findPathWithTimeout(current, origin);
        if (returnSegment.isEmpty()) {
            logger.warn("No return path found");
            return null;
        }

        returnSegment.stream().skip(1).forEach(node -> fullPath.add(node.getXy()));

        int moves = countMoves(fullPath);

        if (moves > caps.maxMoves()) {
            logger.debug("Exceeds maxMoves: {} > {}", moves, caps.maxMoves());
            return null;
        }

        double cost = calculateFlightCost(caps, moves);
        return new SingleFlightResult(orders, fullPath, moves, cost);
    }

    // ==================== Multi-Drone Allocation ====================

    /**
     * Allocate orders optimally across multiple drones using greedy allocation.
     */
    private AllocationResult findOptimalAllocation(
            List<MedDispatchRec> allOrders,
            int[] availableDrones,
            DroneContext context) {

        logger.info("findOptimalAllocation: {} orders, {} drones",
                allOrders.size(), availableDrones.length);

        AllocationResult result = new AllocationResult();
        LinkedHashSet<@NotNull Integer> remaining = allOrders.stream()
                .map(MedDispatchRec::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int round = 0;

        while (!remaining.isEmpty() && round < MAX_ALLOCATION_ROUNDS) {
            round++;
            logger.info("Round {}: {} orders remaining", round, remaining.size());

            boolean progress = false;

            for (int droneId : availableDrones) {
                if (remaining.isEmpty()) break;

                if (!context.hasDroneData(droneId)) {
                    logger.warn("Drone {} missing data", droneId);
                    continue;
                }

                List<MedDispatchRec> availableOrders = allOrders.stream()
                        .filter(order -> remaining.contains(order.id()))
                        .collect(Collectors.toList());

                logger.debug("Trying drone {} with {} remaining orders",
                        droneId, availableOrders.size());

                FlightInfo flight = buildBestFlight(
                        context.getOrigin(droneId),
                        context.getCapability(droneId),
                        availableOrders);

                if (flight != null && !flight.orders.isEmpty()) {
                    logger.info("Drone {} allocated {} orders", droneId, flight.orders.size());

                    result.addFlight(droneId, flight);
                    flight.orders.forEach(order -> remaining.remove(order.id()));
                    progress = true;
                }
            }

            if (!progress) {
                logger.error("No progress in round {} - cannot allocate remaining orders", round);
                return null;
            }
        }

        if (!remaining.isEmpty()) {
            logger.error("Failed to allocate {} orders after {} rounds",
                    remaining.size(), round);
            return null;
        }

        return result;
    }

    /**
     * Build the best flight for a drone given available orders using greedy selection.
     */
    private FlightInfo buildBestFlight(
            LngLat origin,
            DroneCapability caps,
            List<MedDispatchRec> availableOrders) {

        logger.debug("buildBestFlight: {} available orders", availableOrders.size());

        if (availableOrders.isEmpty()) {
            return null;
        }

        // Sort by distance from origin (greedy nearest-first)
        List<MedDispatchRec> sorted = new ArrayList<>(availableOrders);
        sorted.sort(Comparator.comparingDouble(order ->
                order.delivery() != null
                        ? GeometryService.distance(origin, order.delivery())
                        : Double.MAX_VALUE));

        // Greedily select orders that fit constraints
        List<MedDispatchRec> selected = new ArrayList<>();
        double totalCapacity = 0.0;

        for (MedDispatchRec order : sorted) {
            if (order.delivery() == null) continue;

            double requiredCapacity = order.requirements().capacity();

            // Check capacity constraint
            if (totalCapacity + requiredCapacity > caps.capacity()) {
                continue;
            }

            // Try adding this order
            List<MedDispatchRec> testOrders = new ArrayList<>(selected);
            testOrders.add(order);

            FlightInfo testFlight = buildFlightWithHover(origin, caps, testOrders);
            if (testFlight != null) {
                selected.add(order);
                totalCapacity += requiredCapacity;
            }
        }

        if (selected.isEmpty()) {
            logger.debug("No orders could be selected for this drone");
            return null;
        }

        return buildFlightWithHover(origin, caps, selected);
    }

    /**
     * Build complete flight path with hover points at each delivery.
     */
    private FlightInfo buildFlightWithHover(
            LngLat origin,
            DroneCapability caps,
            List<MedDispatchRec> orders) {

        logger.trace("buildFlightWithHover: {} orders", orders.size());

        List<LngLat> fullPath = new ArrayList<>();
        List<Integer> hoverIndices = new ArrayList<>();
        fullPath.add(origin);
        LngLat current = origin;

        // Visit each delivery with hover
        for (MedDispatchRec order : orders) {
            LngLat target = order.delivery();

            logger.trace("Pathfinding to order {}", order.id());
            List<Node> segment = findPathWithTimeout(current, target);

            if (segment.isEmpty()) {
                logger.trace("No path to order {}", order.id());
                return null;
            }

            // Add path segment
            segment.stream().skip(1).forEach(node -> fullPath.add(node.getXy()));

            // Add delivery location and hover
            fullPath.add(target);
            fullPath.add(target); // Hover point

            hoverIndices.add(fullPath.size() - 1);
            current = target;
        }

        // Return to origin
        logger.trace("Pathfinding return to origin");
        List<Node> returnSegment = findPathWithTimeout(current, origin);
        if (returnSegment.isEmpty()) {
            logger.trace("No return path to origin");
            return null;
        }

        returnSegment.stream().skip(1).forEach(node -> fullPath.add(node.getXy()));

        int moves = countMoves(fullPath);
        if (moves > caps.maxMoves()) {
            logger.trace("Exceeds maxMoves: {} > {}", moves, caps.maxMoves());
            return null;
        }

        double cost = calculateFlightCost(caps, moves);
        return new FlightInfo(orders, fullPath, hoverIndices, moves, cost);
    }

    // ==================== A* Pathfinding ====================

    /**
     * Find path using A* algorithm with timeout protection.
     */
    private List<Node> findPathWithTimeout(LngLat origin, LngLat target) {
        long startTime = System.currentTimeMillis();

        // Validate endpoints
        if (isInRestrictedArea(target)) {
            logger.error("Target {} is in restricted area", target);
            return List.of();
        }

        if (isInRestrictedArea(origin)) {
            logger.error("Origin {} is in restricted area", origin);
            return List.of();
        }

        // Check if already at target
        if (GeometryService.isClose(origin, target)) {
            return List.of(new Node(origin, null, target));
        }

        double straightLineDist = GeometryService.distance(origin, target);
        logger.debug("Pathfinding distance: {:.6f} degrees ({:.0f} min steps)",
                straightLineDist, straightLineDist / 0.00015);

        // A* data structures
        PriorityQueue<Node> openQueue = new PriorityQueue<>(
                Comparator.comparingDouble(Node::getFCost));
        Set<String> openSet = new HashSet<>();
        Set<String> closedSet = new HashSet<>();
        NodeMap allNodes = new NodeMap();

        // Initialize with start node
        Node startNode = new Node(origin, null, target);
        String startKey = positionKey(origin);
        openQueue.offer(startNode);
        openSet.add(startKey);
        allNodes.put(startKey, startNode);

        int iterations = 0;

        while (!openQueue.isEmpty() && iterations < MAX_PATHFINDING_ITERATIONS) {
            iterations++;

            // Periodic logging
            if (iterations % PATHFINDING_LOG_INTERVAL == 0) {
                Node peek = openQueue.peek();
                logger.debug("A* iteration {}: open={}, closed={}, best dist={:.6f}",
                        iterations, openQueue.size(), closedSet.size(),
                        peek != null ? GeometryService.distance(peek.getXy(), target) : 0);
            }

            // Check timeout
            if (System.currentTimeMillis() - startTime > MAX_PATHFINDING_TIME_MS) {
                logger.error("A* timeout after {}ms, {} iterations",
                        System.currentTimeMillis() - startTime, iterations);
                return List.of();
            }

            Node current = openQueue.poll();
            if (current == null) break;

            String currentKey = positionKey(current.getXy());
            openSet.remove(currentKey);

            // Check if goal reached
            if (GeometryService.isClose(current.getXy(), target)) {
                List<Node> path = reconstructPath(current);
                logger.debug("A* SUCCESS: {}ms, {} iterations, {} steps",
                        System.currentTimeMillis() - startTime, iterations, path.size());
                return path;
            }

            closedSet.add(currentKey);

            // Explore neighbors
            for (Node neighbour : generateNeighbours(current, target)) {
                if (isInRestrictedArea(neighbour.getXy())) {
                    continue;
                }

                String neighbourKey = positionKey(neighbour.getXy());

                if (closedSet.contains(neighbourKey)) {
                    continue;
                }

                Node existingNode = allNodes.get(neighbourKey);

                if (existingNode == null) {
                    // New node
                    openQueue.offer(neighbour);
                    openSet.add(neighbourKey);
                    allNodes.put(neighbourKey, neighbour);
                } else if (neighbour.getGCost() < existingNode.getGCost()) {
                    // Better path found
                    openQueue.remove(existingNode);
                    existingNode.setGCost(neighbour.getGCost());
                    existingNode.setParent(current);
                    openQueue.offer(existingNode);
                    openSet.add(neighbourKey);
                }
            }
        }

        logger.error("A* FAILED: {}ms, {} iterations (limit: {})",
                System.currentTimeMillis() - startTime, iterations, MAX_PATHFINDING_ITERATIONS);
        return List.of();
    }

    /**
     * Generate all 16-direction neighbors for A* expansion.
     */
    private List<Node> generateNeighbours(Node current, LngLat goal) {
        return Arrays.stream(uk.ac.ed.acp.cw2.data.Directions.Direction16.values())
                .map(direction -> {
                    LngLat nextPos = GeometryService.stepFrom(current.getXy(), direction);
                    return new Node(nextPos, current, goal);
                })
                .collect(Collectors.toList());
    }

    /**
     * Reconstruct path from goal node back to start.
     */
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

    // ==================== Utility Methods ====================

    /**
     * Generate unique position key for hashing.
     */
    private String positionKey(LngLat pos) {
        return String.format("%.9f,%.9f", pos.lng(), pos.lat());
    }

    /**
     * Check if point is in any restricted area (with caching).
     */
    private boolean isInRestrictedArea(LngLat point) {
        return getRestrictedAreas().stream()
                .anyMatch(area -> isInRegion(point, area.vertices()));
    }

    /**
     * Get restricted areas with thread-safe caching.
     */
    private List<RestrictedAreas> getRestrictedAreas() {
        List<RestrictedAreas> cached = restrictedAreasCache.get();
        if (cached == null) {
            cached = droneQueryService.fetchRestrictedAreas();
            restrictedAreasCache.set(cached);
            logger.debug("Cached {} restricted areas", cached.size());
        }
        return cached;
    }

    /**
     * Clear thread-local cache.
     */
    private void clearCache() {
        restrictedAreasCache.remove();
    }

    /**
     * Count actual moves (excluding stationary positions).
     */
    private int countMoves(List<LngLat> flightPath) {
        int moves = 0;
        for (int i = 1; i < flightPath.size(); i++) {
            if (!flightPath.get(i).equals(flightPath.get(i - 1))) {
                moves++;
            }
        }
        return moves;
    }

    /**
     * Calculate total flight cost based on drone capability.
     */
    private double calculateFlightCost(DroneCapability caps, int moves) {
        return caps.costInitial() + caps.costFinal() + moves * caps.costPerMove();
    }

    /**
     * Convert path to GeoJSON LineString format.
     */
    private Map<String, Object> convertToGeoJson(List<LngLat> path) {
        List<List<Double>> coordinates = path.stream()
                .map(p -> List.of(p.lng(), p.lat()))
                .collect(Collectors.toList());

        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "LineString");
        geoJson.put("coordinates", coordinates);
        return geoJson;
    }

    /**
     * Create empty GeoJSON LineString.
     */
    private Map<String, Object> createEmptyGeoJson() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("type", "LineString");
        empty.put("coordinates", List.of());
        return empty;
    }
}