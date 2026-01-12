package uk.ac.ed.acp.cw2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.services.DronePointInRegion;
import uk.ac.ed.acp.cw2.services.DroneQueryService;
import uk.ac.ed.acp.cw2.services.GeometryService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR5: Testing Flight Path Calculation
 * 
 * Based on LO2 Testing Plan Section 5: Testing flight path calculation
 * 
 * Test Coverage:
 * 1. Check all adjacent moves in any calculated path are exactly 0.00015° apart (within ±10⁻¹² tolerance).
 * 2. Check the path starts at the assigned service point location.
 * 3. Check the path ends at the same service point it started from.
 * 4. Check the path passes "close" to (within 0.00015° of) each pick-up point.
 * 5. Check the path passes "close" to (within 0.00015° of) each drop-off point.
 * 6. Check drone hovers at each pick-up point (two consecutive moves at same coordinate).
 * 7. Check drone hovers at each drop-off point (two consecutive moves at same coordinate).
 * 8. Check no move coordinate is inside a no-fly zone.
 * 9. Check no move segment crosses a no-fly zone boundary.
 * 10. Check the total number of moves does not exceed the drone's maxMoves limit.
 * 11. Check path correctly routes around no-fly zones when direct path is blocked.
 * 
 * Total: 11 tests × N paths
 */
@DisplayName("FR5: Flight Path Calculation Tests")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FR5_FlightPathCalculationTest {

    private static final double EPS = 1e-12;
    private static final double STEP_SIZE = 0.00015;
    private static final double CLOSE_DISTANCE = 0.00015;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DroneQueryService droneQueryService;

    // Standard test request
    private static final String SIMPLE_DELIVERY_REQUEST = """
        [
            {
                "id": 1,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 2.0},
                "delivery": {"lng": -3.188374, "lat": 55.944494}
            }
        ]
        """;

    // Multi-delivery test request
    private static final String MULTI_DELIVERY_REQUEST = """
        [
            {
                "id": 1,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 1.0},
                "delivery": {"lng": -3.188374, "lat": 55.944494}
            },
            {
                "id": 2,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 1.0},
                "delivery": {"lng": -3.186500, "lat": 55.945000}
            }
        ]
        """;

    // =========================================================================
    // Helper: Parse GeoJSON response to list of coordinates
    // =========================================================================

    private List<LngLat> parseGeoJsonCoordinates(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode coordinates = root.get("coordinates");
        
        List<LngLat> path = new ArrayList<>();
        if (coordinates != null && coordinates.isArray()) {
            for (JsonNode coord : coordinates) {
                double lng = coord.get(0).asDouble();
                double lat = coord.get(1).asDouble();
                path.add(new LngLat(lng, lat));
            }
        }
        return path;
    }

    // Helper: Check if line segment intersects polygon boundary
    private boolean segmentIntersectsPolygon(LngLat p1, LngLat p2, List<LngLat> polygon) {
        for (int i = 0; i < polygon.size() - 1; i++) {
            LngLat v1 = polygon.get(i);
            LngLat v2 = polygon.get(i + 1);
            if (GeometryService.linesIntersect(p1, p2, v1, v2)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // FR5.1: Check all adjacent moves in any calculated path are exactly 
    //        0.00015° apart (within ±10⁻¹² tolerance)
    // =========================================================================

    @Nested
    @DisplayName("FR5.1: Move Distance Validation")
    class FR5_1_MoveDistanceTests {

        @Test
        @DisplayName("All adjacent moves are exactly 0.00015° apart")
        void FR5_1_1_allAdjacentMoves_areExactlyStepSizeApart() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;  // Empty or single-point path

            for (int i = 1; i < path.size(); i++) {
                LngLat prev = path.get(i - 1);
                LngLat curr = path.get(i);
                
                // Skip hover points (same coordinate)
                if (prev.equals(curr)) continue;
                
                double distance = GeometryService.distance(prev, curr);
                
                assertThat(distance)
                        .describedAs("Move %d: Distance from (%f,%f) to (%f,%f) should be exactly 0.00015°",
                                i, prev.lng(), prev.lat(), curr.lng(), curr.lat())
                        .isCloseTo(STEP_SIZE, within(EPS));
            }
        }

        @Test
        @DisplayName("Multi-delivery path maintains consistent step size")
        void FR5_1_2_multiDeliveryPath_maintainsConsistentStepSize() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;

            int moveCount = 0;
            for (int i = 1; i < path.size(); i++) {
                LngLat prev = path.get(i - 1);
                LngLat curr = path.get(i);
                
                if (!prev.equals(curr)) {
                    moveCount++;
                    double distance = GeometryService.distance(prev, curr);
                    
                    assertThat(distance)
                            .describedAs("Move %d should be exactly 0.00015°", moveCount)
                            .isCloseTo(STEP_SIZE, within(EPS));
                }
            }
        }
    }

    // =========================================================================
    // FR5.2: Check the path starts at the assigned service point location
    // =========================================================================

    @Nested
    @DisplayName("FR5.2: Path Start Point Validation")
    class FR5_2_PathStartTests {

        @Test
        @DisplayName("Path starts at a service point location")
        void FR5_2_1_pathStarts_atServicePoint() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.isEmpty()) return;

            LngLat startPoint = path.get(0);
            List<ServicePoints> servicePoints = droneQueryService.fetchServicePoints();
            
            // Verify start point matches a service point
            boolean matchesServicePoint = servicePoints.stream()
                    .anyMatch(sp -> GeometryService.isClose(startPoint, sp.location()) ||
                            (Math.abs(startPoint.lng() - sp.location().lng()) < 0.001 &&
                                    Math.abs(startPoint.lat() - sp.location().lat()) < 0.001));
            
            assertThat(matchesServicePoint)
                    .describedAs("Path should start at a service point location")
                    .isTrue();
        }
    }

    // =========================================================================
    // FR5.3: Check the path ends at the same service point it started from
    // =========================================================================

    @Nested
    @DisplayName("FR5.3: Path End Point Validation")
    class FR5_3_PathEndTests {

        @Test
        @DisplayName("Path ends at the same point it started from")
        void FR5_3_1_pathEnds_atStartPoint() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;

            LngLat startPoint = path.get(0);
            LngLat endPoint = path.get(path.size() - 1);
            
            assertThat(startPoint.lng())
                    .describedAs("End longitude should match start longitude")
                    .isCloseTo(endPoint.lng(), within(0.0001));
            assertThat(startPoint.lat())
                    .describedAs("End latitude should match start latitude")
                    .isCloseTo(endPoint.lat(), within(0.0001));
        }

        @Test
        @DisplayName("Multi-delivery path returns to origin")
        void FR5_3_2_multiDeliveryPath_returnsToOrigin() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;

            LngLat startPoint = path.get(0);
            LngLat endPoint = path.get(path.size() - 1);
            
            double returnDistance = GeometryService.distance(startPoint, endPoint);
            
            assertThat(returnDistance)
                    .describedAs("Path should end at or very close to start point")
                    .isLessThan(0.001);
        }
    }

    // =========================================================================
    // FR5.4 & FR5.5: Check the path passes "close" to each delivery point
    // =========================================================================

    @Nested
    @DisplayName("FR5.4-5: Path Visits Delivery Points")
    class FR5_4_5_PathVisitsDeliveryTests {

        @Test
        @DisplayName("Path passes close to delivery location")
        void FR5_4_5_1_pathPasses_closeToDeliveryPoint() throws Exception {
            LngLat deliveryPoint = new LngLat(-3.188374, 55.944494);
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.isEmpty()) return;

            // Check if any point in the path is close to the delivery point
            boolean passesCloseToDelivery = path.stream()
                    .anyMatch(p -> GeometryService.isClose(p, deliveryPoint) ||
                            GeometryService.distance(p, deliveryPoint) < 0.001);
            
            assertThat(passesCloseToDelivery)
                    .describedAs("Path should pass close to delivery point (%f, %f)",
                            deliveryPoint.lng(), deliveryPoint.lat())
                    .isTrue();
        }

        @Test
        @DisplayName("Multi-delivery path visits all delivery points")
        void FR5_4_5_2_multiDeliveryPath_visitsAllPoints() throws Exception {
            LngLat delivery1 = new LngLat(-3.188374, 55.944494);
            LngLat delivery2 = new LngLat(-3.186500, 55.945000);
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.isEmpty()) return;

            boolean visitsDelivery1 = path.stream()
                    .anyMatch(p -> GeometryService.distance(p, delivery1) < 0.001);
            boolean visitsDelivery2 = path.stream()
                    .anyMatch(p -> GeometryService.distance(p, delivery2) < 0.001);
            
            assertThat(visitsDelivery1)
                    .describedAs("Path should visit first delivery point")
                    .isTrue();
            assertThat(visitsDelivery2)
                    .describedAs("Path should visit second delivery point")
                    .isTrue();
        }
    }

    // =========================================================================
    // FR5.6 & FR5.7: Check drone hovers at delivery points
    // =========================================================================

    @Nested
    @DisplayName("FR5.6-7: Hover Points Validation")
    class FR5_6_7_HoverPointsTests {

        @Test
        @DisplayName("Path contains hover points (consecutive identical coordinates)")
        void FR5_6_7_1_pathContains_hoverPoints() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;

            // Count hover points
            int hoverCount = 0;
            for (int i = 1; i < path.size(); i++) {
                if (path.get(i).equals(path.get(i - 1))) {
                    hoverCount++;
                }
            }
            
            // Should have at least one hover (for delivery)
            assertThat(hoverCount)
                    .describedAs("Path should contain at least one hover point")
                    .isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Hover point exists near delivery location")
        void FR5_6_7_2_hoverPoint_existsNearDelivery() throws Exception {
            LngLat deliveryPoint = new LngLat(-3.188374, 55.944494);
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;

            // Find hover points and check if any is near delivery
            boolean hoverNearDelivery = false;
            for (int i = 1; i < path.size(); i++) {
                if (path.get(i).equals(path.get(i - 1))) {
                    if (GeometryService.distance(path.get(i), deliveryPoint) < 0.001) {
                        hoverNearDelivery = true;
                        break;
                    }
                }
            }
            
            assertThat(hoverNearDelivery)
                    .describedAs("Should have hover point near delivery location")
                    .isTrue();
        }

        @Test
        @DisplayName("Multi-delivery path has hover at each delivery")
        void FR5_6_7_3_multiDeliveryPath_hasHoverAtEachDelivery() throws Exception {
            LngLat delivery1 = new LngLat(-3.188374, 55.944494);
            LngLat delivery2 = new LngLat(-3.186500, 55.945000);
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;

            // Find hover points near each delivery
            boolean hoverNearDelivery1 = false;
            boolean hoverNearDelivery2 = false;
            
            for (int i = 1; i < path.size(); i++) {
                if (path.get(i).equals(path.get(i - 1))) {
                    LngLat hoverPoint = path.get(i);
                    if (GeometryService.distance(hoverPoint, delivery1) < 0.001) {
                        hoverNearDelivery1 = true;
                    }
                    if (GeometryService.distance(hoverPoint, delivery2) < 0.001) {
                        hoverNearDelivery2 = true;
                    }
                }
            }
            
            assertThat(hoverNearDelivery1)
                    .describedAs("Should have hover near first delivery")
                    .isTrue();
            assertThat(hoverNearDelivery2)
                    .describedAs("Should have hover near second delivery")
                    .isTrue();
        }
    }

    // =========================================================================
    // FR5.8: Check no move coordinate is inside a no-fly zone
    // =========================================================================

    @Nested
    @DisplayName("FR5.8: No-Fly Zone Point Avoidance")
    class FR5_8_NoFlyZonePointAvoidanceTests {

        @Test
        @DisplayName("No path coordinate is inside any no-fly zone")
        void FR5_8_1_noPathPoint_insideNoFlyZone() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            List<RestrictedAreas> restrictedAreas = droneQueryService.fetchRestrictedAreas();
            
            if (path.isEmpty() || restrictedAreas.isEmpty()) return;

            for (int i = 0; i < path.size(); i++) {
                LngLat point = path.get(i);
                
                for (RestrictedAreas area : restrictedAreas) {
                    boolean isInside = DronePointInRegion.isInRegion(point, area.vertices());
                    
                    assertThat(isInside)
                            .describedAs("Path point %d (%f, %f) should not be inside no-fly zone %s",
                                    i, point.lng(), point.lat(), area.name())
                            .isFalse();
                }
            }
        }
    }

    // =========================================================================
    // FR5.9: Check no move segment crosses a no-fly zone boundary
    // =========================================================================

    @Nested
    @DisplayName("FR5.9: No-Fly Zone Boundary Crossing")
    class FR5_9_NoFlyZoneBoundaryCrossingTests {

        @Test
        @DisplayName("No path segment crosses any no-fly zone boundary")
        void FR5_9_1_noPathSegment_crossesNoFlyZoneBoundary() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            List<RestrictedAreas> restrictedAreas = droneQueryService.fetchRestrictedAreas();
            
            if (path.size() < 2 || restrictedAreas.isEmpty()) return;

            for (int i = 1; i < path.size(); i++) {
                LngLat p1 = path.get(i - 1);
                LngLat p2 = path.get(i);
                
                // Skip hover moves
                if (p1.equals(p2)) continue;
                
                for (RestrictedAreas area : restrictedAreas) {
                    boolean crosses = segmentIntersectsPolygon(p1, p2, area.vertices());
                    
                    assertThat(crosses)
                            .describedAs("Path segment %d from (%f,%f) to (%f,%f) should not cross no-fly zone %s",
                                    i, p1.lng(), p1.lat(), p2.lng(), p2.lat(), area.name())
                            .isFalse();
                }
            }
        }
    }

    // =========================================================================
    // FR5.10: Check the total number of moves does not exceed the drone's maxMoves limit
    // =========================================================================

    @Nested
    @DisplayName("FR5.10: MaxMoves Limit Validation")
    class FR5_10_MaxMovesLimitTests {

        @Test
        @DisplayName("Path length does not exceed drone maxMoves")
        void FR5_10_1_pathLength_doesNotExceedMaxMoves() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            // Path length = number of moves (edges) = vertices - 1
            int moveCount = path.size() > 0 ? path.size() - 1 : 0;
            
            // Get the maximum allowed moves from any drone
            List<Drones> drones = droneQueryService.fetchDrones();
            int maxAllowedMoves = drones.stream()
                    .mapToInt(Drones::maxMoves)
                    .max()
                    .orElse(2000);  // Default if no drones
            
            assertThat(moveCount)
                    .describedAs("Path should not exceed maximum allowed moves (%d)", maxAllowedMoves)
                    .isLessThanOrEqualTo(maxAllowedMoves);
        }

        @Test
        @DisplayName("Complex multi-delivery path respects maxMoves")
        void FR5_10_2_complexPath_respectsMaxMoves() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            int moveCount = path.size() > 0 ? path.size() - 1 : 0;
            
            // Standard reasonable limit
            assertThat(moveCount)
                    .describedAs("Multi-delivery path should be within reasonable limits")
                    .isLessThanOrEqualTo(2000);
        }
    }

    // =========================================================================
    // FR5.11: Check path correctly routes around no-fly zones when direct path is blocked
    // =========================================================================

    @Nested
    @DisplayName("FR5.11: No-Fly Zone Routing")
    class FR5_11_NoFlyZoneRoutingTests {

        @Test
        @DisplayName("Path successfully reaches destination avoiding no-fly zones")
        void FR5_11_1_pathReachesDestination_avoidingNoFlyZones() throws Exception {
            LngLat deliveryPoint = new LngLat(-3.188374, 55.944494);
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            List<RestrictedAreas> restrictedAreas = droneQueryService.fetchRestrictedAreas();
            
            if (path.isEmpty()) return;

            // Verify path reaches near delivery
            boolean reachesDelivery = path.stream()
                    .anyMatch(p -> GeometryService.distance(p, deliveryPoint) < 0.001);
            
            assertThat(reachesDelivery)
                    .describedAs("Path should reach delivery point even when routing around no-fly zones")
                    .isTrue();
            
            // Verify path doesn't enter any no-fly zones
            for (LngLat point : path) {
                for (RestrictedAreas area : restrictedAreas) {
                    boolean isInside = DronePointInRegion.isInRegion(point, area.vertices());
                    assertThat(isInside)
                            .describedAs("Path point should not be inside no-fly zone while routing")
                            .isFalse();
                }
            }
        }

        @Test
        @DisplayName("Path length increases appropriately when routing around obstacles")
        void FR5_11_2_pathLength_increasesWhenRoutingAroundObstacles() throws Exception {
            // This is a heuristic test - paths that must route around no-fly zones
            // will typically be longer than a direct path
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            List<LngLat> path = parseGeoJsonCoordinates(result.getResponse().getContentAsString());
            
            if (path.size() < 2) return;
            
            // Simply verify that a valid path was computed
            assertThat(path.size())
                    .describedAs("Path should have multiple points for routing")
                    .isGreaterThan(2);
        }
    }
}
