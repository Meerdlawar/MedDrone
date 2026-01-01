package uk.ac.ed.acp.cw2.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TRUE Integration Tests
 *
 * These tests:
 * - Load the FULL Spring application context
 * - Use REAL services (no mocking)
 * - Hit the ACTUAL external ILP REST API
 * - Test the complete request/response flow
 *
 * Note: These tests require network access to the ILP REST service
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Integration Tests - Full Stack")
class FullStackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // Health Check
    // =========================================================================

    @Test
    @DisplayName("Application health check returns UP")
    void healthCheck_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // =========================================================================
    // Drone Details Tests
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/droneDetails/{id}")
    class DroneDetailsIntegrationTests {

        @Test
        @DisplayName("Get drone details for existing drone")
        void getDroneDetails_existingDrone_returnsDrone() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{id}", 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").exists())
                    .andExpect(jsonPath("$.capability").exists())
                    .andExpect(jsonPath("$.capability.cooling").exists())
                    .andExpect(jsonPath("$.capability.heating").exists())
                    .andExpect(jsonPath("$.capability.capacity").exists())
                    .andExpect(jsonPath("$.capability.maxMoves").exists())
                    .andExpect(jsonPath("$.capability.costPerMove").exists())
                    .andExpect(jsonPath("$.capability.costInitial").exists())
                    .andExpect(jsonPath("$.capability.costFinal").exists());
        }

        @Test
        @DisplayName("Get drone details for non-existing drone returns 404")
        void getDroneDetails_nonExistingDrone_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{id}", 99999))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // Drones With Cooling Tests
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/dronesWithCooling/{state}")
    class DronesWithCoolingIntegrationTests {

        @Test
        @DisplayName("Get drones with cooling=true returns array of IDs")
        void dronesWithCooling_true_returnsArray() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        }

        @Test
        @DisplayName("Get drones with cooling=false returns array of IDs")
        void dronesWithCooling_false_returnsArray() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        }
    }

    // =========================================================================
    // Query As Path Tests
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/queryAsPath/{attribute}/{value}")
    class QueryAsPathIntegrationTests {

        @Test
        @DisplayName("Query for capacity=8 returns matching drones")
        void queryAsPath_capacity8_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attribute}/{value}", "capacity", "8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query for maxMoves=1500 returns matching drones")
        void queryAsPath_maxMoves1500_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attribute}/{value}", "maxMoves", "1500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query for costPerMove=0.07 returns matching drones")
        void queryAsPath_costPerMove007_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attribute}/{value}", "costPerMove", "0.07"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query for cooling=true returns matching drones")
        void queryAsPath_coolingTrue_returnsMatching() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attribute}/{value}", "cooling", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // Query (POST) Tests
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/query")
    class QueryPostIntegrationTests {

        @Test
        @DisplayName("Query with costPerMove < 0.04 AND maxMoves > 1000")
        void query_costPerMoveAndMaxMoves_returnsMatching() throws Exception {
            String body = """
                    [
                        {"attribute":"costPerMove","operator":"<","value":"0.04"},
                        {"attribute":"maxMoves","operator":">","value":"1000"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with costFinal = 3.5 AND maxMoves = 1500")
        void query_costFinalAndMaxMoves_returnsMatching() throws Exception {
            String body = """
                    [
                        {"attribute":"costFinal","operator":"=","value":"3.5"},
                        {"attribute":"maxMoves","operator":"=","value":"1500"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with impossible maxMoves returns empty array")
        void query_impossibleMaxMoves_returnsEmpty() throws Exception {
            String body = """
                    [
                        {"attribute":"maxMoves","operator":"=","value":"9999999"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // =========================================================================
    // Query Available Drones Tests
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/queryAvailableDrones")
    class QueryAvailableDronesIntegrationTests {

        @Test
        @DisplayName("Capacity on a Friday 14:30 returns available drones")
        void queryAvailableDrones_capacityFriday_returnsAvailable() throws Exception {
            String body = """
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
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        }

        @Test
        @DisplayName("Capacity and Cooling on a Friday 14:30 returns available drones")
        void queryAvailableDrones_capacityAndCoolingFriday_returnsAvailable() throws Exception {
            String body = """
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
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Impossible capacity returns empty array")
        void queryAvailableDrones_impossibleCapacity_returnsEmpty() throws Exception {
            String body = """
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
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("maxCost 0.01 returns empty (no drone has costPerMove < 0.01)")
        void queryAvailableDrones_maxCost001_returnsEmpty() throws Exception {
            String body = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 1, "maxCost": 0.01},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("maxCost 0.02 returns drones with costPerMove < 0.02")
        void queryAvailableDrones_maxCost002_returnsSome() throws Exception {
            String body = """
                    [
                        {
                            "id": 1,
                            "date": "2025-12-12",
                            "time": "14:30",
                            "requirements": {"capacity": 1, "maxCost": 0.02},
                            "delivery": {"lng": -3.188374, "lat": 55.944494}
                        }
                    ]
                    """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        }
    }

    // =========================================================================
    // Geometry Endpoint Tests
    // =========================================================================

    @Nested
    @DisplayName("Geometry Endpoints")
    class GeometryIntegrationTests {

        @Test
        @DisplayName("distanceTo returns valid distance")
        void distanceTo_validPositions_returnsDistance() throws Exception {
            String body = """
                    {
                        "position1": {"lng": -3.192473, "lat": 55.946233},
                        "position2": {"lng": -3.192473, "lat": 55.942617}
                    }
                    """;

            mockMvc.perform(post("/api/v1/distanceTo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isNumber());
        }

        @Test
        @DisplayName("isCloseTo returns boolean")
        void isCloseTo_validPositions_returnsBoolean() throws Exception {
            String body = """
                    {
                        "position1": {"lng": -3.192473, "lat": 55.946233},
                        "position2": {"lng": -3.192473, "lat": 55.946117}
                    }
                    """;

            mockMvc.perform(post("/api/v1/isCloseTo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isBoolean());
        }

        @Test
        @DisplayName("nextPosition returns new position")
        void nextPosition_validInput_returnsPosition() throws Exception {
            String body = """
                    {
                        "start": {"lng": -3.192473, "lat": 55.946233},
                        "angle": 90
                    }
                    """;

            mockMvc.perform(post("/api/v1/nextPosition")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lng").exists())
                    .andExpect(jsonPath("$.lat").exists());
        }

        @Test
        @DisplayName("isInRegion returns boolean for point inside")
        void isInRegion_pointInside_returnsTrue() throws Exception {
            String body = """
                    {
                        "position": {"lng": -3.186000, "lat": 55.944000},
                        "region": {
                            "name": "central",
                            "vertices": [
                                {"lng": -3.192473, "lat": 55.946233},
                                {"lng": -3.192473, "lat": 55.942617},
                                {"lng": -3.184319, "lat": 55.942617},
                                {"lng": -3.184319, "lat": 55.946233},
                                {"lng": -3.192473, "lat": 55.946233}
                            ]
                        }
                    }
                    """;

            mockMvc.perform(post("/api/v1/isInRegion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(true));
        }
    }

    // =========================================================================
    // Delivery Path Tests
    // =========================================================================

    @Nested
    @DisplayName("Delivery Path Endpoints")
    class DeliveryPathIntegrationTests {

        @Test
        @DisplayName("calcDeliveryPath returns valid plan")
        void calcDeliveryPath_validOrder_returnsPlan() throws Exception {
            String body = """
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

            mockMvc.perform(post("/api/v1/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").exists())
                    .andExpect(jsonPath("$.totalMoves").exists())
                    .andExpect(jsonPath("$.dronePaths").isArray());
        }

        @Test
        @DisplayName("calcDeliveryPathAsGeoJson returns valid GeoJSON")
        void calcDeliveryPathAsGeoJson_validOrder_returnsGeoJson() throws Exception {
            String body = """
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

            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("LineString"))
                    .andExpect(jsonPath("$.coordinates").isArray());
        }
    }
}