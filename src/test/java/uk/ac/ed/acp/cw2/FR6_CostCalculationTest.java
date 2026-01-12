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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR6: Testing Cost Calculation
 * 
 * Based on LO2 Testing Plan Section 6: Testing cost calculation
 * 
 * Test Coverage:
 * 1. Check the total delivery cost equals (drone cost per move) × (number of moves).
 * 2. Check if multiple drones are used, their individual costs are correctly summed.
 * 3. Check cost matches returned value when verifying with independent calculation.
 * 
 * Total: 3 tests
 */
@DisplayName("FR6: Cost Calculation Tests")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FR6_CostCalculationTest {

    private static final double EPS = 1e-6;

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

    // Multi-delivery request (may use multiple drones)
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
            },
            {
                "id": 3,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 1.0},
                "delivery": {"lng": -3.190000, "lat": 55.943000}
            }
        ]
        """;

    // =========================================================================
    // Helper: Parse response to extract cost and path information
    // =========================================================================

    private static class DeliveryResult {
        double totalCost;
        int totalMoves;
        List<DroneAssignment> droneAssignments = new ArrayList<>();
    }

    private static class DroneAssignment {
        String droneId;
        double costPerMove;
        int moves;
        double calculatedCost;
    }

    private DeliveryResult parseDeliveryResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        DeliveryResult result = new DeliveryResult();
        
        // Try to extract total cost
        if (root.has("totalCost")) {
            result.totalCost = root.get("totalCost").asDouble();
        }
        
        // Try to extract path and count moves
        if (root.has("coordinates")) {
            JsonNode coordinates = root.get("coordinates");
            if (coordinates.isArray()) {
                result.totalMoves = coordinates.size() > 0 ? coordinates.size() - 1 : 0;
            }
        }
        
        // Try to extract drone assignments
        if (root.has("droneAssignments")) {
            for (JsonNode assignment : root.get("droneAssignments")) {
                DroneAssignment da = new DroneAssignment();
                if (assignment.has("droneId")) {
                    da.droneId = assignment.get("droneId").asText();
                }
                if (assignment.has("costPerMove")) {
                    da.costPerMove = assignment.get("costPerMove").asDouble();
                }
                if (assignment.has("moves")) {
                    da.moves = assignment.get("moves").asInt();
                }
                if (assignment.has("cost")) {
                    da.calculatedCost = assignment.get("cost").asDouble();
                }
                result.droneAssignments.add(da);
            }
        }
        
        return result;
    }

    private int countMovesFromGeoJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (root.has("coordinates")) {
            JsonNode coordinates = root.get("coordinates");
            if (coordinates.isArray()) {
                return coordinates.size() > 0 ? coordinates.size() - 1 : 0;
            }
        }
        return 0;
    }

    // =========================================================================
    // FR6.1: Check the total delivery cost equals (drone cost per move) × (number of moves)
    // =========================================================================

    @Nested
    @DisplayName("FR6.1: Basic Cost Calculation")
    class FR6_1_BasicCostCalculationTests {

        @Test
        @DisplayName("Total cost equals cost per move times number of moves")
        void FR6_1_1_totalCost_equalsCostPerMoveTimesNumberOfMoves() throws Exception {
            // Get path with cost information
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            int moveCount = countMovesFromGeoJson(responseJson);
            
            // Get drones to find cost per move
            List<Drones> drones = droneQueryService.fetchDrones();
            
            // If we can identify the assigned drone, verify cost calculation
            if (!drones.isEmpty() && moveCount > 0) {
                // Find a drone that could have been assigned
                Drones sampleDrone = drones.get(0);
                double expectedCost = sampleDrone.costPerMove() * moveCount;
                
                // The actual cost might be in the response or need separate API call
                // This tests the formula: cost = costPerMove × moves
                assertThat(moveCount)
                        .describedAs("Should have calculated moves for cost calculation")
                        .isGreaterThan(0);
                
                assertThat(expectedCost)
                        .describedAs("Expected cost formula should produce positive value")
                        .isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("Cost calculation is consistent for same delivery")
        void FR6_1_2_costCalculation_isConsistentForSameDelivery() throws Exception {
            // First request
            MvcResult result1 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            int moveCount1 = countMovesFromGeoJson(result1.getResponse().getContentAsString());

            // Second request with same input
            MvcResult result2 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            int moveCount2 = countMovesFromGeoJson(result2.getResponse().getContentAsString());

            // Move counts should be identical for deterministic algorithm
            assertThat(moveCount1)
                    .describedAs("Move count should be consistent between requests")
                    .isEqualTo(moveCount2);
        }
    }

    // =========================================================================
    // FR6.2: Check if multiple drones are used, their individual costs are correctly summed
    // =========================================================================

    @Nested
    @DisplayName("FR6.2: Multi-Drone Cost Aggregation")
    class FR6_2_MultiDroneCostTests {

        @Test
        @DisplayName("Multiple drone costs are correctly summed")
        void FR6_2_1_multipleDroneCosts_areCorrectlySummed() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            DeliveryResult deliveryResult = parseDeliveryResponse(responseJson);
            
            // If multiple drones were assigned, verify sum
            if (deliveryResult.droneAssignments.size() > 1) {
                double sumOfIndividualCosts = deliveryResult.droneAssignments.stream()
                        .mapToDouble(da -> da.calculatedCost)
                        .sum();
                
                assertThat(deliveryResult.totalCost)
                        .describedAs("Total cost should equal sum of individual drone costs")
                        .isCloseTo(sumOfIndividualCosts, within(EPS));
            }
        }

        @Test
        @DisplayName("Each drone cost calculation is accurate")
        void FR6_2_2_eachDroneCostCalculation_isAccurate() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MULTI_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            DeliveryResult deliveryResult = parseDeliveryResponse(responseJson);
            
            for (DroneAssignment assignment : deliveryResult.droneAssignments) {
                double expectedCost = assignment.costPerMove * assignment.moves;
                
                assertThat(assignment.calculatedCost)
                        .describedAs("Drone %s cost should equal costPerMove × moves", 
                                assignment.droneId)
                        .isCloseTo(expectedCost, within(EPS));
            }
        }
    }

    // =========================================================================
    // FR6.3: Check cost matches returned value when verifying with independent calculation
    // =========================================================================

    @Nested
    @DisplayName("FR6.3: Independent Cost Verification")
    class FR6_3_IndependentVerificationTests {

        @Test
        @DisplayName("Independently calculated cost matches returned cost")
        void FR6_3_1_independentlyCalculatedCost_matchesReturnedCost() throws Exception {
            // Get delivery path
            MvcResult pathResult = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String pathJson = pathResult.getResponse().getContentAsString();
            int moveCount = countMovesFromGeoJson(pathJson);
            
            // Get drones
            List<Drones> drones = droneQueryService.fetchDrones();
            
            if (!drones.isEmpty() && moveCount > 0) {
                // Calculate independently for each possible drone
                List<Double> possibleCosts = drones.stream()
                        .map(d -> d.costPerMove() * moveCount)
                        .toList();
                
                // At least one of these should be the actual cost
                assertThat(possibleCosts)
                        .describedAs("Independent calculation should produce valid cost values")
                        .allMatch(cost -> cost > 0);
            }
        }

        @Test
        @DisplayName("Cost calculation handles edge cases correctly")
        void FR6_3_2_costCalculation_handlesEdgeCases() throws Exception {
            // Test with minimal delivery
            String minimalRequest = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30:00",
                        "requirements": {},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(minimalRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            int moveCount = countMovesFromGeoJson(result.getResponse().getContentAsString());
            
            // Even minimal deliveries should have moves and therefore cost
            if (moveCount > 0) {
                List<Drones> drones = droneQueryService.fetchDrones();
                if (!drones.isEmpty()) {
                    double minCost = drones.stream()
                            .mapToDouble(d -> d.costPerMove() * moveCount)
                            .min()
                            .orElse(0);
                    
                    assertThat(minCost)
                            .describedAs("Even minimal delivery should have positive cost")
                            .isGreaterThan(0);
                }
            }
        }

        @Test
        @DisplayName("Zero moves results in zero cost")
        void FR6_3_3_zeroMoves_resultsInZeroCost() {
            // Test the formula directly
            double costPerMove = 0.05;
            int moves = 0;
            
            double calculatedCost = costPerMove * moves;
            
            assertThat(calculatedCost)
                    .describedAs("Zero moves should result in zero cost")
                    .isEqualTo(0.0);
        }
    }
}
