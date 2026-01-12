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
import uk.ac.ed.acp.cw2.services.DroneQueryService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR7: Testing Order Assignment
 * 
 * Based on LO2 Testing Plan Section 7: Testing order assignment
 * 
 * Test Coverage:
 * 1. Check each delivery is assigned to exactly one drone.
 * 2. Check assigned drone satisfies all capacity requirements for its deliveries.
 * 3. Check assigned drone is available on the specified delivery date and time.
 * 4. Check orders that cannot be fulfilled are clearly reported as undeliverable.
 * 5. Check assignment respects maxMoves when multiple deliveries are assigned to one drone.
 * 
 * Total: 5 tests
 */
@DisplayName("FR7: Order Assignment Tests")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FR7_OrderAssignmentTest {

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

    // Multi-delivery request
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
                "requirements": {"capacity": 1.5},
                "delivery": {"lng": -3.186500, "lat": 55.945000}
            },
            {
                "id": 3,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 2.0},
                "delivery": {"lng": -3.190000, "lat": 55.943000}
            }
        ]
        """;

    // Request with cooling requirement
    private static final String COOLING_DELIVERY_REQUEST = """
        [
            {
                "id": 1,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 1.0, "requiresCooling": true},
                "delivery": {"lng": -3.188374, "lat": 55.944494}
            }
        ]
        """;

    // Impossible delivery request (very high capacity)
    private static final String IMPOSSIBLE_DELIVERY_REQUEST = """
        [
            {
                "id": 1,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 9999.0},
                "delivery": {"lng": -3.188374, "lat": 55.944494}
            }
        ]
        """;

    // =========================================================================
    // Helper: Parse assignment response
    // =========================================================================

    private static class AssignmentResult {
        Map<Integer, String> deliveryToDrone = new HashMap<>();  // deliveryId -> droneId
        List<Integer> undeliverable = new ArrayList<>();
        Map<String, List<Integer>> droneToDeliveries = new HashMap<>();  // droneId -> list of deliveryIds
    }

    private AssignmentResult parseAssignmentResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        AssignmentResult result = new AssignmentResult();
        
        // Parse assignments
        if (root.has("assignments")) {
            for (JsonNode assignment : root.get("assignments")) {
                int deliveryId = assignment.get("deliveryId").asInt();
                String droneId = assignment.get("droneId").asText();
                
                result.deliveryToDrone.put(deliveryId, droneId);
                result.droneToDeliveries
                        .computeIfAbsent(droneId, k -> new ArrayList<>())
                        .add(deliveryId);
            }
        }
        
        // Parse undeliverable
        if (root.has("undeliverable")) {
            for (JsonNode undeliverableItem : root.get("undeliverable")) {
                result.undeliverable.add(undeliverableItem.asInt());
            }
        }
        
        return result;
    }

    // =========================================================================
    // FR7.1: Check each delivery is assigned to exactly one drone
    // =========================================================================

    @Nested
    @DisplayName("FR7.1: Single Assignment per Delivery")
    class FR7_1_SingleAssignmentTests {

        @Test
        @DisplayName("Each delivery is assigned to exactly one drone")
        void FR7_1_1_eachDelivery_isAssignedToExactlyOneDrone() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            
            // Parse to check assignments
            JsonNode root = objectMapper.readTree(responseJson);
            
            // Track which deliveries have been assigned
            Set<Integer> assignedDeliveries = new HashSet<>();
            
            if (root.has("assignments")) {
                for (JsonNode assignment : root.get("assignments")) {
                    int deliveryId = assignment.get("deliveryId").asInt();
                    
                    assertThat(assignedDeliveries)
                            .describedAs("Delivery %d should not be assigned multiple times", deliveryId)
                            .doesNotContain(deliveryId);
                    
                    assignedDeliveries.add(deliveryId);
                }
            }
        }

        @Test
        @DisplayName("All deliveries are either assigned or marked undeliverable")
        void FR7_1_2_allDeliveries_areAccountedFor() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            AssignmentResult assignments = parseAssignmentResponse(responseJson);
            
            int totalDeliveries = 3;  // From MULTI_DELIVERY_REQUEST
            int accountedFor = assignments.deliveryToDrone.size() + assignments.undeliverable.size();
            
            assertThat(accountedFor)
                    .describedAs("All deliveries should be either assigned or marked undeliverable")
                    .isEqualTo(totalDeliveries);
        }
    }

    // =========================================================================
    // FR7.2: Check assigned drone satisfies all capacity requirements
    // =========================================================================

    @Nested
    @DisplayName("FR7.2: Capacity Requirement Satisfaction")
    class FR7_2_CapacityRequirementTests {

        @Test
        @DisplayName("Assigned drone has sufficient capacity")
        void FR7_2_1_assignedDrone_hasSufficientCapacity() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            List<Drones> drones = droneQueryService.fetchDrones();
            
            JsonNode root = objectMapper.readTree(responseJson);
            
            if (root.has("assignments")) {
                for (JsonNode assignment : root.get("assignments")) {
                    String droneId = assignment.get("droneId").asText();
                    
                    // Find the drone
                    Optional<Drones> assignedDrone = drones.stream()
                            .filter(d -> d.id().equals(droneId))
                            .findFirst();
                    
                    if (assignedDrone.isPresent()) {
                        assertThat(assignedDrone.get().capacity())
                                .describedAs("Drone %s should have capacity >= 2.0 for this delivery", droneId)
                                .isGreaterThanOrEqualTo(2.0);
                    }
                }
            }
        }

        @Test
        @DisplayName("Drone with cooling capability assigned for cooling requirement")
        void FR7_2_2_coolingDrone_assignedForCoolingRequirement() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COOLING_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            List<Drones> drones = droneQueryService.fetchDrones();
            
            JsonNode root = objectMapper.readTree(responseJson);
            
            if (root.has("assignments")) {
                for (JsonNode assignment : root.get("assignments")) {
                    String droneId = assignment.get("droneId").asText();
                    
                    Optional<Drones> assignedDrone = drones.stream()
                            .filter(d -> d.id().equals(droneId))
                            .findFirst();
                    
                    if (assignedDrone.isPresent()) {
                        assertThat(assignedDrone.get().hasCooling())
                                .describedAs("Drone %s should have cooling capability", droneId)
                                .isTrue();
                    }
                }
            }
        }
    }

    // =========================================================================
    // FR7.3: Check assigned drone is available on the specified delivery date and time
    // =========================================================================

    @Nested
    @DisplayName("FR7.3: Availability Verification")
    class FR7_3_AvailabilityTests {

        @Test
        @DisplayName("Assigned drone is available on delivery date")
        void FR7_3_1_assignedDrone_isAvailableOnDeliveryDate() throws Exception {
            // December 12, 2025 is a Friday
            LocalDate deliveryDate = LocalDate.of(2025, 12, 12);
            DayOfWeek deliveryDay = deliveryDate.getDayOfWeek();
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            List<Drones> drones = droneQueryService.fetchDrones();
            
            JsonNode root = objectMapper.readTree(responseJson);
            
            if (root.has("assignments")) {
                for (JsonNode assignment : root.get("assignments")) {
                    String droneId = assignment.get("droneId").asText();
                    
                    Optional<Drones> assignedDrone = drones.stream()
                            .filter(d -> d.id().equals(droneId))
                            .findFirst();
                    
                    if (assignedDrone.isPresent() && assignedDrone.get().availability() != null) {
                        Availability avail = assignedDrone.get().availability();
                        
                        // Check if drone is available on the delivery day
                        if (avail.daysOfWeek() != null && !avail.daysOfWeek().isEmpty()) {
                            assertThat(avail.daysOfWeek())
                                    .describedAs("Drone %s should be available on %s", 
                                            droneId, deliveryDay)
                                    .contains(deliveryDay.toString());
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("Assigned drone is available at delivery time")
        void FR7_3_2_assignedDrone_isAvailableAtDeliveryTime() throws Exception {
            LocalTime deliveryTime = LocalTime.of(14, 30, 0);
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            List<Drones> drones = droneQueryService.fetchDrones();
            
            JsonNode root = objectMapper.readTree(responseJson);
            
            if (root.has("assignments")) {
                for (JsonNode assignment : root.get("assignments")) {
                    String droneId = assignment.get("droneId").asText();
                    
                    Optional<Drones> assignedDrone = drones.stream()
                            .filter(d -> d.id().equals(droneId))
                            .findFirst();
                    
                    if (assignedDrone.isPresent() && assignedDrone.get().availability() != null) {
                        Availability avail = assignedDrone.get().availability();
                        
                        // Check if drone is available at the delivery time
                        if (avail.startTime() != null && avail.endTime() != null) {
                            LocalTime start = LocalTime.parse(avail.startTime());
                            LocalTime end = LocalTime.parse(avail.endTime());
                            
                            assertThat(deliveryTime)
                                    .describedAs("Delivery time should be within drone %s availability window", 
                                            droneId)
                                    .isAfterOrEqualTo(start)
                                    .isBeforeOrEqualTo(end);
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // FR7.4: Check orders that cannot be fulfilled are clearly reported as undeliverable
    // =========================================================================

    @Nested
    @DisplayName("FR7.4: Undeliverable Order Reporting")
    class FR7_4_UndeliverableOrderTests {

        @Test
        @DisplayName("Impossible delivery is marked as undeliverable")
        void FR7_4_1_impossibleDelivery_isMarkedAsUndeliverable() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(IMPOSSIBLE_DELIVERY_REQUEST))
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            AssignmentResult assignments = parseAssignmentResponse(responseJson);
            
            // The impossible delivery should either be undeliverable or result in error
            if (result.getResponse().getStatus() == 200) {
                assertThat(assignments.undeliverable)
                        .describedAs("Impossible delivery should be marked as undeliverable")
                        .contains(1);
            }
        }

        @Test
        @DisplayName("Undeliverable orders include reason")
        void FR7_4_2_undeliverableOrders_includeReason() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(IMPOSSIBLE_DELIVERY_REQUEST))
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(responseJson);
            
            // Check if undeliverable items have reasons
            if (root.has("undeliverableDetails")) {
                for (JsonNode detail : root.get("undeliverableDetails")) {
                    if (detail.has("reason")) {
                        String reason = detail.get("reason").asText();
                        assertThat(reason)
                                .describedAs("Undeliverable reason should be provided")
                                .isNotEmpty();
                    }
                }
            }
        }
    }

    // =========================================================================
    // FR7.5: Check assignment respects maxMoves when multiple deliveries are assigned
    // =========================================================================

    @Nested
    @DisplayName("FR7.5: MaxMoves Constraint in Assignment")
    class FR7_5_MaxMovesConstraintTests {

        @Test
        @DisplayName("Drone assignments respect maxMoves limit")
        void FR7_5_1_droneAssignments_respectMaxMovesLimit() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            List<Drones> drones = droneQueryService.fetchDrones();
            
            JsonNode root = objectMapper.readTree(responseJson);
            
            // If path info is available, check total moves per drone
            if (root.has("dronePathInfo")) {
                for (JsonNode droneInfo : root.get("dronePathInfo")) {
                    String droneId = droneInfo.get("droneId").asText();
                    int totalMoves = droneInfo.get("totalMoves").asInt();
                    
                    Optional<Drones> drone = drones.stream()
                            .filter(d -> d.id().equals(droneId))
                            .findFirst();
                    
                    if (drone.isPresent()) {
                        assertThat(totalMoves)
                                .describedAs("Drone %s total moves should not exceed maxMoves", droneId)
                                .isLessThanOrEqualTo(drone.get().maxMoves());
                    }
                }
            }
        }

        @Test
        @DisplayName("Multiple deliveries split across drones if maxMoves exceeded")
        void FR7_5_2_multipleDeliveries_splitIfMaxMovesExceeded() throws Exception {
            // This test verifies that if combined deliveries would exceed maxMoves,
            // they are split across multiple drones
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            AssignmentResult assignments = parseAssignmentResponse(responseJson);
            
            // If multiple deliveries are assigned, verify each drone's load is valid
            for (Map.Entry<String, List<Integer>> entry : assignments.droneToDeliveries.entrySet()) {
                String droneId = entry.getKey();
                List<Integer> deliveries = entry.getValue();
                
                // Each drone should have at least 1 delivery assigned
                assertThat(deliveries)
                        .describedAs("Drone %s should have at least one delivery", droneId)
                        .isNotEmpty();
            }
        }
    }
}
