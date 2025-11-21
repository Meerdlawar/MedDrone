package uk.ac.ed.acp.cw2.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ed.acp.cw2.dto.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DroneRoutingService Tests")
class DroneRoutingServiceTest {

    @Mock
    private DroneAvailabilityService availabilityService;

    @Mock
    private DroneQueryService droneQueryService;

    private DroneRoutingService routingService;

    // Test data
    private LngLat servicePoint;
    private LngLat deliveryPoint1;
    private LngLat deliveryPoint2;
    private DroneCapability standardCapability;
    private DroneInfo testDrone;
    private MedDispatchRec testOrder1;
    private MedDispatchRec testOrder2;

    @BeforeEach
    void setUp() {
        routingService = new DroneRoutingService(availabilityService, droneQueryService);

        // Setup test coordinates
        servicePoint = new LngLat(-3.186358, 55.944680);
        deliveryPoint1 = new LngLat(-3.188, 55.945);
        deliveryPoint2 = new LngLat(-3.187, 55.946);

        // Setup standard drone capability
        standardCapability = new DroneCapability(
                false,      // cooling
                true,       // heating
                10.0,       // capacity
                2000,       // maxMoves
                0.01,       // costPerMove
                1.0,        // costInitial
                1.0         // costFinal
        );

        testDrone = new DroneInfo("Test Drone", 1, standardCapability);

        // Setup test orders
        testOrder1 = new MedDispatchRec(
                1,
                LocalDate.of(2025, 12, 22),
                LocalTime.of(13, 0),
                new DispatchRequirements(2.0, false, true, null),
                deliveryPoint1
        );

        testOrder2 = new MedDispatchRec(
                2,
                LocalDate.of(2025, 12, 22),
                LocalTime.of(13, 30),
                new DispatchRequirements(1.5, false, true, null),
                deliveryPoint2
        );
    }

    @Nested
    @DisplayName("calcDeliveryPlan Tests")
    class CalcDeliveryPlanTests {

        @Test
        @DisplayName("Returns empty plan when orders list is null")
        void calcDeliveryPlan_nullOrders_returnsEmptyPlan() {
            DeliveryPlan plan = routingService.calcDeliveryPlan(null);

            assertNotNull(plan);
            assertEquals(0.0, plan.totalCost());
            assertEquals(0, plan.totalMoves());
            assertTrue(plan.dronePaths().isEmpty());

            verifyNoInteractions(availabilityService, droneQueryService);
        }

        @Test
        @DisplayName("Returns empty plan when orders list is empty")
        void calcDeliveryPlan_emptyOrders_returnsEmptyPlan() {
            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of());

            assertNotNull(plan);
            assertEquals(0.0, plan.totalCost());
            assertEquals(0, plan.totalMoves());
            assertTrue(plan.dronePaths().isEmpty());

            verifyNoInteractions(availabilityService, droneQueryService);
        }

        @Test
        @DisplayName("Returns empty plan when no drones are available")
        void calcDeliveryPlan_noDronesAvailable_returnsEmptyPlan() {
            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[0]);

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1));

            assertNotNull(plan);
            assertEquals(0.0, plan.totalCost());
            assertEquals(0, plan.totalMoves());
            assertTrue(plan.dronePaths().isEmpty());

            verify(availabilityService).queryAvailableDrones(anyList());
            verifyNoInteractions(droneQueryService);
        }

        @Test
        @DisplayName("Successfully allocates single order to single drone")
        void calcDeliveryPlan_singleOrder_successfulAllocation() {
            // Mock availability
            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});

            // Mock drone data
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of()); // No restrictions

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1));

            assertNotNull(plan);
            assertTrue(plan.totalCost() > 0);
            assertTrue(plan.totalMoves() > 0);
            assertEquals(1, plan.dronePaths().size());

            DronePath dronePath = plan.dronePaths().get(0);
            assertEquals(1, dronePath.droneId());
            assertEquals(1, dronePath.deliveries().size());

            DeliveryPath delivery = dronePath.deliveries().get(0);
            assertEquals(testOrder1.id(), delivery.deliveryId());
            assertFalse(delivery.flightPath().isEmpty());

            // Verify hover exists (two consecutive identical points)
            assertHoverPresent(delivery.flightPath());
        }

        @Test
        @DisplayName("Successfully allocates multiple orders to single drone")
        void calcDeliveryPlan_multipleOrders_successfulAllocation() {
            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1, testOrder2));

            assertNotNull(plan);
            assertTrue(plan.totalCost() > 0);
            assertTrue(plan.totalMoves() > 0);
            assertEquals(1, plan.dronePaths().size());

            DronePath dronePath = plan.dronePaths().get(0);
            assertEquals(2, dronePath.deliveries().size());
        }

        @Test
        @DisplayName("Returns empty plan when delivery is in restricted area")
        void calcDeliveryPlan_deliveryInRestrictedArea_returnsEmptyPlan() {
            // Create restricted area that contains delivery point
            RestrictedAreas restrictedArea = new RestrictedAreas(
                    "Test Restriction",
                    1,
                    new Limits(0, -1),
                    List.of(
                            new LngLat(-3.190, 55.946),
                            new LngLat(-3.190, 55.944),
                            new LngLat(-3.186, 55.944),
                            new LngLat(-3.186, 55.946),
                            new LngLat(-3.190, 55.946)
                    )
            );

            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of(restrictedArea));

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1));

            assertNotNull(plan);
            assertEquals(0.0, plan.totalCost());
            assertEquals(0, plan.totalMoves());
            assertTrue(plan.dronePaths().isEmpty());
        }

        @Test
        @DisplayName("Returns empty plan when drone lacks capacity")
        void calcDeliveryPlan_insufficientCapacity_returnsEmptyPlan() {
            DroneCapability smallCapability = new DroneCapability(
                    false, true, 1.0, 2000, 0.01, 1.0, 1.0 // Only 1.0 capacity
            );
            DroneInfo smallDrone = new DroneInfo("Small Drone", 1, smallCapability);

            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(smallDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            // testOrder1 requires 2.0 capacity
            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1));

            assertNotNull(plan);
            assertTrue(plan.dronePaths().isEmpty());
        }
    }

    @Nested
    @DisplayName("calcDeliveryPathAsGeoJson Tests")
    class CalcDeliveryPathAsGeoJsonTests {

        @Test
        @DisplayName("Returns empty GeoJSON when orders list is null")
        void calcGeoJson_nullOrders_returnsEmptyGeoJson() {
            Map<String, Object> geoJson = routingService.calcDeliveryPathAsGeoJson(null);

            assertNotNull(geoJson);
            assertEquals("LineString", geoJson.get("type"));
            assertTrue(((List<?>) geoJson.get("coordinates")).isEmpty());

            verifyNoInteractions(availabilityService, droneQueryService);
        }

        @Test
        @DisplayName("Returns empty GeoJSON when orders list is empty")
        void calcGeoJson_emptyOrders_returnsEmptyGeoJson() {
            Map<String, Object> geoJson = routingService.calcDeliveryPathAsGeoJson(List.of());

            assertNotNull(geoJson);
            assertEquals("LineString", geoJson.get("type"));
            assertTrue(((List<?>) geoJson.get("coordinates")).isEmpty());
        }

        @Test
        @DisplayName("Returns empty GeoJSON when no drones are available")
        void calcGeoJson_noDronesAvailable_returnsEmptyGeoJson() {
            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[0]);

            Map<String, Object> geoJson = routingService.calcDeliveryPathAsGeoJson(List.of(testOrder1));

            assertNotNull(geoJson);
            assertEquals("LineString", geoJson.get("type"));
            assertTrue(((List<?>) geoJson.get("coordinates")).isEmpty());
        }

        @Test
        @DisplayName("Successfully generates GeoJSON for single order")
        void calcGeoJson_singleOrder_returnsValidGeoJson() {
            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            Map<String, Object> geoJson = routingService.calcDeliveryPathAsGeoJson(List.of(testOrder1));

            assertNotNull(geoJson);
            assertEquals("LineString", geoJson.get("type"));

            @SuppressWarnings("unchecked")
            List<List<Double>> coordinates = (List<List<Double>>) geoJson.get("coordinates");

            assertNotNull(coordinates);
            assertFalse(coordinates.isEmpty());

            // First coordinate should be service point
            assertEquals(servicePoint.lng(), coordinates.get(0).get(0), 0.0001);
            assertEquals(servicePoint.lat(), coordinates.get(0).get(1), 0.0001);

            // Last coordinate should be back at service point
            List<Double> lastCoord = coordinates.get(coordinates.size() - 1);
            assertEquals(servicePoint.lng(), lastCoord.get(0), 0.0001);
            assertEquals(servicePoint.lat(), lastCoord.get(1), 0.0001);
        }

        @Test
        @DisplayName("Successfully generates GeoJSON for multiple orders")
        void calcGeoJson_multipleOrders_returnsValidGeoJson() {
            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            Map<String, Object> geoJson = routingService.calcDeliveryPathAsGeoJson(
                    List.of(testOrder1, testOrder2));

            assertNotNull(geoJson);
            assertEquals("LineString", geoJson.get("type"));

            @SuppressWarnings("unchecked")
            List<List<Double>> coordinates = (List<List<Double>>) geoJson.get("coordinates");

            assertFalse(coordinates.isEmpty());
            assertTrue(coordinates.size() > 10); // Should have multiple waypoints
        }

        @Test
        @DisplayName("Returns empty GeoJSON when delivery is unreachable")
        void calcGeoJson_unreachableDelivery_returnsEmptyGeoJson() {
            RestrictedAreas restrictedArea = new RestrictedAreas(
                    "Test Restriction",
                    1,
                    new Limits(0, -1),
                    List.of(
                            new LngLat(-3.190, 55.946),
                            new LngLat(-3.190, 55.944),
                            new LngLat(-3.186, 55.944),
                            new LngLat(-3.186, 55.946),
                            new LngLat(-3.190, 55.946)
                    )
            );

            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of(restrictedArea));

            Map<String, Object> geoJson = routingService.calcDeliveryPathAsGeoJson(List.of(testOrder1));

            assertNotNull(geoJson);
            assertEquals("LineString", geoJson.get("type"));
            assertTrue(((List<?>) geoJson.get("coordinates")).isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles order with null delivery location")
        void handlesOrderWithNullDelivery() {
            MedDispatchRec badOrder = new MedDispatchRec(
                    99,
                    LocalDate.of(2025, 12, 22),
                    LocalTime.of(13, 0),
                    new DispatchRequirements(2.0, false, true, null),
                    null // null delivery
            );

            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(badOrder));

            assertNotNull(plan);
            assertTrue(plan.dronePaths().isEmpty());
        }

        @Test
        @DisplayName("Handles drone with insufficient maxMoves")
        void handlesInsufficientMaxMoves() {
            DroneCapability limitedCapability = new DroneCapability(
                    false, true, 10.0, 10, 0.01, 1.0, 1.0 // Only 10 moves
            );
            DroneInfo limitedDrone = new DroneInfo("Limited Drone", 1, limitedCapability);

            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(1, servicePoint));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(limitedDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1));

            assertNotNull(plan);
            // Should return empty plan as 10 moves is insufficient
            assertTrue(plan.dronePaths().isEmpty());
        }

        @Test
        @DisplayName("Handles missing drone origin location")
        void handlesMissingDroneOrigin() {
            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of()); // Empty map
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1));

            assertNotNull(plan);
            assertTrue(plan.dronePaths().isEmpty());
        }

        @Test
        @DisplayName("Handles multiple drones with different capabilities")
        void handlesMultipleDrones() {
            DroneCapability capability2 = new DroneCapability(
                    true, false, 15.0, 3000, 0.02, 2.0, 2.0
            );
            DroneInfo drone2 = new DroneInfo("Drone 2", 2, capability2);

            when(availabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{1, 2});
            when(droneQueryService.fetchDroneOriginLocations())
                    .thenReturn(Map.of(
                            1, servicePoint,
                            2, new LngLat(-3.177, 55.981)
                    ));
            when(droneQueryService.fetchDrones())
                    .thenReturn(List.of(testDrone, drone2));
            when(droneQueryService.fetchRestrictedAreas())
                    .thenReturn(List.of());

            DeliveryPlan plan = routingService.calcDeliveryPlan(List.of(testOrder1, testOrder2));

            assertNotNull(plan);
            assertTrue(plan.totalMoves() > 0);
        }
    }

    // Helper method to verify hover exists in flight path
    private void assertHoverPresent(List<LngLat> flightPath) {
        assertNotNull(flightPath);
        assertTrue(flightPath.size() >= 2, "FlightPath should have at least 2 points");

        boolean hoverFound = false;
        for (int i = 1; i < flightPath.size(); i++) {
            if (flightPath.get(i).equals(flightPath.get(i - 1))) {
                hoverFound = true;
                break;
            }
        }

        assertTrue(hoverFound, "Expected at least one hover (two identical consecutive LngLat)");
    }
}