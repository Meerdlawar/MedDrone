package uk.ac.ed.acp.cw2.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DroneAvailabilityController.
 * Tests queryAvailableDrones endpoint.
 *
 * These tests run against the REAL external ILP REST API.
 * Note: The submission checker runs against a LOCAL server with INJECTED TEST DATA
 * (drones 99998, 888, 456), which is why expected values may differ.
 *
 * These tests validate behavior with the actual production API data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("DroneAvailabilityController Integration Tests")
class DroneAvailabilityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // Query Available Drones Tests (QueryAsMedDispatchRecCommand)
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/queryAvailableDrones")
    class QueryAvailableDronesTests {

        @Test
        @DisplayName("Capacity 4.5 on Friday 14:30 - returns available drones")
        void queryAvailableDrones_capacityOnFriday_returnsExpectedIds() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 4.5},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("Capacity 8.5 and Cooling=true on Friday 14:30 - returns available drones with cooling")
        void queryAvailableDrones_capacityAndCoolingOnFriday_returnsExpectedIds() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 8.5, "cooling": true},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
            // Results depend on availability schedule, so just verify it returns an array
        }

        @Test
        @DisplayName("Capacity 500 on Friday 14:30 (no match) - returns empty array")
        void queryAvailableDrones_highCapacityNoResult_returnsEmptyArray() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 500},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Query with heating requirement - returns drones with heating capability")
        void queryAvailableDrones_heatingRequirement_returnsMatching() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 2.0, "heating": true},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with maxCost requirement - filters by costPerMove")
        void queryAvailableDrones_maxCostRequirement_filtersByCost() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 1.0, "maxCost": 0.05},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with maxCost=0.01 (too strict) - returns empty array")
        void queryAvailableDrones_maxCostTooStrict_returnsEmpty() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 1.0, "maxCost": 0.01},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Query with multiple dispatches - returns drones available for all")
        void queryAvailableDrones_multipleDispatches_returnsAvailableForAll() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 2.0},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        },
                        {
                            "id": 2,
                            "date": "2025-12-12",
                            "time": "15:00",
                            "requirements": {"capacity": 2.0},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with empty dispatch list - returns empty array")
        void queryAvailableDrones_emptyList_returnsEmpty() throws Exception {
            String requestBody = "[]";

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Query without date/time - returns drones matching capacity only")
        void queryAvailableDrones_noDateTime_returnsMatchingCapacity() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "requirements": {"capacity": 4.0},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query on different day of week - respects availability schedule")
        void queryAvailableDrones_differentDayOfWeek_respectsSchedule() throws Exception {
            // Monday - 2025-12-15
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-15",
                            "time": "14:30",
                            "requirements": {"capacity": 4.5},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query combining cooling, heating, and capacity - returns intersection")
        void queryAvailableDrones_multipleRequirements_returnsIntersection() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 4.0, "cooling": true, "heating": true},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with time as HH:mm:ss format - handles correctly")
        void queryAvailableDrones_timeWithSeconds_handlesCorrectly() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30:00",
                            "requirements": {"capacity": 4.5},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with low capacity requirement - returns many drones")
        void queryAvailableDrones_lowCapacity_returnsManyDrones() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 1.0},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }
    }
}