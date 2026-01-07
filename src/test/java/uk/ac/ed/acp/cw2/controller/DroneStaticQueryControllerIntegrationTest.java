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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DroneStaticQueryController.
 * Tests droneDetails and dronesWithCooling endpoints.
 * 
 * These tests validate the exact behavior expected by the submission checker.
 * Note: These tests require network access to the ILP REST service.
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
        @DisplayName("Get drones details 99998 - returns drone info with all capability fields")
        void getDroneDetails_99998_returnsDroneInfo() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", 99998))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(99998))
                    .andExpect(jsonPath("$.name").value("Drone 99998"))
                    .andExpect(jsonPath("$.capability.cooling").value(false))
                    .andExpect(jsonPath("$.capability.heating").value(false))
                    .andExpect(jsonPath("$.capability.capacity").value(12.0))
                    .andExpect(jsonPath("$.capability.maxMoves").value(1500.0))
                    .andExpect(jsonPath("$.capability.costPerMove").value(0.07))
                    .andExpect(jsonPath("$.capability.costInitial").value(1.4))
                    .andExpect(jsonPath("$.capability.costFinal").value(3.5));
        }

        @Test
        @DisplayName("Get invalid drones details 99999 - returns 404 Not Found")
        void getDroneDetails_99999_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/droneDetails/{droneId}", 99999))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Get drone details for drone 1 - returns valid drone")
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
    }

    // =========================================================================
    // Drones With Cooling Tests (GetDronesCoolingCheckCommand)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/dronesWithCooling/{state}")
    class DronesWithCoolingTests {

        @Test
        @DisplayName("Get drones with cooling=true - returns [1,5,8,9,888]")
        void getDronesWithCooling_true_returnsExpectedIds() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$", containsInAnyOrder(1, 5, 8, 9, 888)));
        }

        @Test
        @DisplayName("Get drones without cooling (cooling=false) - returns [2,3,4,6,7,10,99998,456]")
        void getDronesWithCooling_false_returnsExpectedIds() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", false))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(8)))
                    .andExpect(jsonPath("$", containsInAnyOrder(2, 3, 4, 6, 7, 10, 99998, 456)));
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
    }
}
