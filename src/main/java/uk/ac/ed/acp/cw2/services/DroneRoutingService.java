package uk.ac.ed.acp.cw2.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.data.Directions.Direction16;
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
    private static final long MAX_PATHFINDING_TIME_MS = 10_000;
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

        logger.debug("buildSimpleSingleFlight: {} orders from origin ({}, {})",
                orders.size(), origin.lng(), origin.lat());

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

            logger.debug("Finding path to order {}: ({}, {}) -> ({}, {})",
                    order.id(), current.lng(), current.lat(), target.lng(), target.lat());

            List<Node> segment = findPathWithTimeout(current, target);
            if (segment.isEmpty()) {
                logger.warn("No path found to order {}", order.id());
                return null;
            }

            // Add segment (skip first point as it's current position)
            for (int i = 1; i < segment.size(); i++) {
                fullPath.add(segment.get(i).getXy());
            }

            // Add hover point (delivery)
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

        for (int i = 1; i < returnSegment.size(); i++) {
            fullPath.add(returnSegment.get(i).getXy());
        }

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
        Set<Integer> remaining = allOrders.stream()
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

        logger.debug("buildBestFlight: {} available orders from origin ({}, {})",
                availableOrders.size(), origin.lng(), origin.lat());

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
            for (int i = 1; i < segment.size(); i++) {
                fullPath.add(segment.get(i).getXy());
            }

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

        for (int i = 1; i < returnSegment.size(); i++) {
            fullPath.add(returnSegment.get(i).getXy());
        }

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
            logger.warn("Target ({}, {}) is in restricted area", target.lng(), target.lat());
            return List.of();
        }

        if (isInRestrictedArea(origin)) {
            logger.warn("Origin ({}, {}) is in restricted area", origin.lng(), origin.lat());
            return List.of();
        }

        // Check if already at target
        if (GeometryService.isClose(origin, target)) {
            return List.of(new Node(origin, null, target));
        }

        double straightLineDist = GeometryService.distance(origin, target);
        int estimatedSteps = (int) Math.ceil(straightLineDist / 0.00015);
        logger.debug("Pathfinding from ({}, {}) to ({}, {}): distance={}, ~{} steps",
                String.format("%.6f", origin.lng()), String.format("%.6f", origin.lat()),
                String.format("%.6f", target.lng()), String.format("%.6f", target.lat()),
                String.format("%.6f", straightLineDist), estimatedSteps);

        // A* data structures
        PriorityQueue<Node> openQueue = new PriorityQueue<>(
                Comparator.comparingDouble(Node::getFCost));
        Map<String, Double> bestGCost = new HashMap<>();

        // Initialize with start node
        Node startNode = new Node(origin, null, target);
        String startKey = positionKey(origin);
        openQueue.offer(startNode);
        bestGCost.put(startKey, 0.0);

        int iterations = 0;

        while (!openQueue.isEmpty() && iterations < MAX_PATHFINDING_ITERATIONS) {
            iterations++;

            // Check timeout periodically
            if (iterations % 1000 == 0) {
                if (System.currentTimeMillis() - startTime > MAX_PATHFINDING_TIME_MS) {
                    logger.error("A* timeout after {}ms, {} iterations",
                            System.currentTimeMillis() - startTime, iterations);
                    return List.of();
                }
            }

            // Periodic logging
            if (iterations % PATHFINDING_LOG_INTERVAL == 0) {
                logger.debug("A* iteration {}: open={}, positions tracked={}",
                        iterations, openQueue.size(), bestGCost.size());
            }

            Node current = openQueue.poll();
            if (current == null) break;

            String currentKey = positionKey(current.getXy());

            // Skip if we've already found a better path to this position
            Double recordedGCost = bestGCost.get(currentKey);
            if (recordedGCost != null && current.getGCost() > recordedGCost + 1e-9) {
                continue;
            }

            // Check if goal reached
            if (GeometryService.isClose(current.getXy(), target)) {
                List<Node> path = reconstructPath(current);
                logger.debug("A* SUCCESS: {}ms, {} iterations, {} steps",
                        System.currentTimeMillis() - startTime, iterations, path.size());
                return path;
            }

            // Explore neighbors
            for (Node neighbour : generateNeighbours(current, target)) {
                // Check if move crosses restricted area
                if (moveCrossesRestrictedArea(current.getXy(), neighbour.getXy())) {
                    continue;
                }

                String neighbourKey = positionKey(neighbour.getXy());

                // Check if we've found a better path to this neighbor
                Double existingGCost = bestGCost.get(neighbourKey);
                if (existingGCost == null || neighbour.getGCost() < existingGCost - 1e-9) {
                    bestGCost.put(neighbourKey, neighbour.getGCost());
                    openQueue.offer(neighbour);
                }
            }
        }

        logger.error("A* FAILED after {}ms, {} iterations (no path found)",
                System.currentTimeMillis() - startTime, iterations);
        return List.of();
    }

    /**
     * Generate neighbors prioritized by direction toward goal.
     */
    private List<Node> generateNeighbours(Node current, LngLat goal) {
        LngLat currentPos = current.getXy();

        // Calculate angle to goal
        double dx = goal.lng() - currentPos.lng();
        double dy = goal.lat() - currentPos.lat();
        double angleToGoal = Math.toDegrees(Math.atan2(dy, dx));
        if (angleToGoal < 0) angleToGoal += 360;

        // Generate all neighbors
        List<Node> neighbors = new ArrayList<>();
        for (Direction16 direction : Direction16.values()) {
            LngLat nextPos = GeometryService.stepFrom(currentPos, direction);
            neighbors.add(new Node(nextPos, current, goal));
        }

        // Sort by how close the direction is to the goal direction
        final double targetAngle = angleToGoal;
        neighbors.sort((a, b) -> {
            double angleA = getDirectionAngle(currentPos, a.getXy());
            double angleB = getDirectionAngle(currentPos, b.getXy());
            double diffA = Math.abs(angleDifference(angleA, targetAngle));
            double diffB = Math.abs(angleDifference(angleB, targetAngle));
            return Double.compare(diffA, diffB);
        });

        return neighbors;
    }

    /**
     * Get angle from one point to another in degrees (0-360).
     */
    private double getDirectionAngle(LngLat from, LngLat to) {
        double dx = to.lng() - from.lng();
        double dy = to.lat() - from.lat();
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;
        return angle;
    }

    /**
     * Calculate the smallest difference between two angles.
     */
    private double angleDifference(double angle1, double angle2) {
        double diff = angle1 - angle2;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return diff;
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

    // ==================== Restricted Area Checking ====================

    /**
     * Check if the line segment from 'from' to 'to' crosses any restricted area.
     */
    private boolean moveCrossesRestrictedArea(LngLat from, LngLat to) {
        List<RestrictedAreas> areas = getRestrictedAreas();

        for (RestrictedAreas area : areas) {
            List<LngLat> vertices = area.vertices();

            // Check if destination is inside
            if (isInRegion(to, vertices)) {
                return true;
            }

            // Check if the line segment intersects any edge of the polygon
            for (int i = 0; i < vertices.size() - 1; i++) {
                LngLat v1 = vertices.get(i);
                LngLat v2 = vertices.get(i + 1);

                if (lineSegmentsIntersect(from, to, v1, v2)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if two line segments intersect.
     * Segment 1: p1 to p2
     * Segment 2: p3 to p4
     */
    private boolean lineSegmentsIntersect(LngLat p1, LngLat p2, LngLat p3, LngLat p4) {
        double d1 = crossProductDirection(p3, p4, p1);
        double d2 = crossProductDirection(p3, p4, p2);
        double d3 = crossProductDirection(p1, p2, p3);
        double d4 = crossProductDirection(p1, p2, p4);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }

        double eps = 1e-10;
        if (Math.abs(d1) < eps && pointOnSegment(p3, p4, p1)) return true;
        if (Math.abs(d2) < eps && pointOnSegment(p3, p4, p2)) return true;
        if (Math.abs(d3) < eps && pointOnSegment(p1, p2, p3)) return true;
        if (Math.abs(d4) < eps && pointOnSegment(p1, p2, p4)) return true;

        return false;
    }

    /**
     * Calculate cross product direction for line intersection.
     */
    private double crossProductDirection(LngLat pi, LngLat pj, LngLat pk) {
        return (pk.lng() - pi.lng()) * (pj.lat() - pi.lat()) -
                (pj.lng() - pi.lng()) * (pk.lat() - pi.lat());
    }

    /**
     * Check if point pk lies on segment pi-pj (assuming collinear).
     */
    private boolean pointOnSegment(LngLat pi, LngLat pj, LngLat pk) {
        return Math.min(pi.lng(), pj.lng()) <= pk.lng() + 1e-10 &&
                pk.lng() <= Math.max(pi.lng(), pj.lng()) + 1e-10 &&
                Math.min(pi.lat(), pj.lat()) <= pk.lat() + 1e-10 &&
                pk.lat() <= Math.max(pi.lat(), pj.lat()) + 1e-10;
    }

    /**
     * Check if point is in any restricted area.
     */
    private boolean isInRestrictedArea(LngLat point) {
        for (RestrictedAreas area : getRestrictedAreas()) {
            if (isInRegion(point, area.vertices())) {
                return true;
            }
        }
        return false;
    }

    // ==================== Utility Methods ====================

    /**
     * Generate unique position key using grid coordinates.
     * This avoids floating-point precision issues by snapping to a grid.
     */
    private String positionKey(LngLat pos) {
        // Step size is 0.00015, so divide by that to get grid position
        long gridLng = Math.round(pos.lng() / 0.00015);
        long gridLat = Math.round(pos.lat() / 0.00015);
        return gridLng + "," + gridLat;
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
     * Count actual moves (excluding stationary hover positions).
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