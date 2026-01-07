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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DroneStaticQueryController.
 * Tests droneDetails and dronesWithCooling endpoints.
 *
 * These tests run against the REAL external ILP REST API.
 * Note: The submission checker runs against a LOCAL server with INJECTED TEST DATA
 * (drones 99998, 888, 456), which is why expected values may differ.
 *
 * These tests validate behavior with the actual production API data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("DroneStaticQueryController Integration Tests")
class DroneStaticQueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // Drone Details Tests (GetDroneDetailsCommand)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/droneDetails/{droneId}")
    class DroneDetailsTests {

        @Test
        @DisplayName("Get drone details for drone 1 - returns valid drone with all fields")
        void getDroneDetails_drone1_returnsDroneInfo() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", 1))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").exists())
                    .andExpect(jsonPath("$.capability").exists())
                    .andExpect(jsonPath("$.capability.cooling").isBoolean())
                    .andExpect(jsonPath("$.capability.heating").isBoolean())
                    .andExpect(jsonPath("$.capability.capacity").isNumber())
                    .andExpect(jsonPath("$.capability.maxMoves").isNumber())
                    .andExpect(jsonPath("$.capability.costPerMove").isNumber())
                    .andExpect(jsonPath("$.capability.costInitial").isNumber())
                    .andExpect(jsonPath("$.capability.costFinal").isNumber());
        }

        @Test
        @DisplayName("Get drone details for drone 5 - returns valid drone")
        void getDroneDetails_drone5_returnsDroneInfo() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", 5))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.name").exists())
                    .andExpect(jsonPath("$.capability").exists());
        }

        @Test
        @DisplayName("Get drone details for drone 10 - returns valid drone")
        void getDroneDetails_drone10_returnsDroneInfo() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", 10))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").exists())
                    .andExpect(jsonPath("$.capability").exists());
        }

        @Test
        @DisplayName("Get invalid drone details 99999 - returns 404 Not Found")
        void getDroneDetails_99999_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", 99999))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Get invalid drone details 0 - returns 404 Not Found")
        void getDroneDetails_drone0_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", 0))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Get invalid drone details -1 - returns 404 Not Found")
        void getDroneDetails_negativeId_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", -1))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // Drones With Cooling Tests (GetDronesCoolingCheckCommand)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/dronesWithCooling/{state}")
    class DronesWithCoolingTests {

        @Test
        @DisplayName("Get drones with cooling=true - returns array containing drones with cooling")
        void getDronesWithCooling_true_returnsExpectedIds() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    // Real API has drones 1, 5, 8, 9 with cooling=true
                    .andExpect(jsonPath("$", hasItems(1, 5, 8, 9)));
        }

        @Test
        @DisplayName("Get drones without cooling (cooling=false) - returns array containing drones without cooling")
        void getDronesWithCooling_false_returnsExpectedIds() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", false))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    // Real API has drones 2, 3, 4, 6, 7, 10 without cooling
                    .andExpect(jsonPath("$", hasItems(2, 3, 4, 6, 7, 10)));
        }

        @Test
        @DisplayName("Get drones with cooling=true - returns array of integers")
        void getDronesWithCooling_true_returnsIntegerArray() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[*]", everyItem(isA(Integer.class))));
        }

        @Test
        @DisplayName("Get drones with cooling=false - returns array of integers")
        void getDronesWithCooling_false_returnsIntegerArray() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", false))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[*]", everyItem(isA(Integer.class))));
        }

        @Test
        @DisplayName("Cooling true and false lists should not overlap")
        void getDronesWithCooling_noOverlap() throws Exception {
            // Get cooling=true drones
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    // These should NOT be in cooling=true
                    .andExpect(jsonPath("$", not(hasItem(2))))
                    .andExpect(jsonPath("$", not(hasItem(3))))
                    .andExpect(jsonPath("$", not(hasItem(4))));
        }
    }
}