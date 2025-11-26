package uk.ac.ed.acp.cw2.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ed.acp.cw2.dto.DroneCapability;
import uk.ac.ed.acp.cw2.dto.DroneInfo;
import uk.ac.ed.acp.cw2.services.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.services.DroneQueryService;
import uk.ac.ed.acp.cw2.services.GraphQLDataService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Mock MVC tests for CW2 endpoints only.
 *
 * Adjust the controllers = {...} to your real controller class
 * (I’m guessing Cw2EndpointsController).
 */
@WebMvcTest(controllers = DroneStaticQueryController.class)
class DroneStaticQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock your *real* services
    @MockitoBean
    private DroneQueryService droneQueryService;

    @MockitoBean
    private DroneAvailabilityService droneAvailabilityService;

    // ADD THIS: Mock the GraphQLDataService
    @MockitoBean
    private GraphQLDataService graphQLDataService;

    // -------------------------------------------------------------------------
    // 1) /dronesWithCooling/{state}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/dronesWithCooling/{state}")
    class DronesWithCoolingTests {

        @Test
        @DisplayName("returns drone IDs that support cooling when state=true")
        void dronesWithCooling_true_returnsIds() throws Exception {
            List<DroneInfo> drones = List.of(
                    new DroneInfo("Drone 1", 1,
                            new DroneCapability(true, false, 4.0, 4000, 0.02, 2.0, 2.5)),
                    new DroneInfo("Drone 2", 2,
                            new DroneCapability(false, false, 6.0, 5000, 0.03, 3.0, 3.5)),
                    new DroneInfo("Drone 4", 4,
                            new DroneCapability(true, false, 8.0, 6000, 0.04, 4.0, 4.5)),
                    new DroneInfo("Drone 7", 7,
                            new DroneCapability(true, false, 8.0, 6000, 0.04, 4.0, 4.5))
            );

            // Controller is expected to filter based on capability.cooling
            when(droneQueryService.fetchDrones()).thenReturn(drones);

            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0]").value(1))
                    .andExpect(jsonPath("$[1]").value(4))
                    .andExpect(jsonPath("$[2]").value(7));
        }

        @Test
        @DisplayName("returns drone IDs that do NOT support cooling when state=false")
        void dronesWithCooling_false_returnsIds() throws Exception {
            List<DroneInfo> drones = List.of(
                    new DroneInfo("Drone 1", 1,
                            new DroneCapability(true, false, 4.0, 4000, 0.02, 2.0, 2.5)),
                    new DroneInfo("Drone 2", 2,
                            new DroneCapability(false, false, 6.0, 5000, 0.03, 3.0, 3.5)),
                    new DroneInfo("Drone 3", 3,
                            new DroneCapability(false, true, 8.0, 6000, 0.04, 4.0, 4.5))
            );

            when(droneQueryService.fetchDrones()).thenReturn(drones);

            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", false))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0]").value(2))
                    .andExpect(jsonPath("$[1]").value(3));
        }
    }

    // -------------------------------------------------------------------------
    // 2) /droneDetails/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/droneDetails/{id}")
    class DroneDetailsTests {

        @Test
        @DisplayName("returns drone details for valid id")
        void droneDetails_validId_returnsDrone() throws Exception {
            DroneInfo target = new DroneInfo(
                    "Drone 4",
                    4,
                    new DroneCapability(true, false, 8.0, 6000, 0.03, 3.4, 4.5)
            );
            List<DroneInfo> drones = List.of(
                    new DroneInfo("Drone 1", 1,
                            new DroneCapability(false, false, 4.0, 4000, 0.02, 2.0, 2.5)),
                    target
            );

            when(droneQueryService.fetchDrones()).thenReturn(drones);

            mockMvc.perform(get("/api/v1/droneDetails/{id}", 4))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(4))
                    .andExpect(jsonPath("$.name").value("Drone 4"))
                    .andExpect(jsonPath("$.capability.cooling").value(true))
                    .andExpect(jsonPath("$.capability.capacity").value(8.0));
        }

        @Test
        @DisplayName("returns 404 when drone id not found")
        void droneDetails_invalidId_returns404() throws Exception {
            List<DroneInfo> drones = List.of(
                    new DroneInfo("Drone 1", 1,
                            new DroneCapability(false, false, 4.0, 4000, 0.02, 2.0, 2.5))
            );

            when(droneQueryService.fetchDrones()).thenReturn(drones);

            mockMvc.perform(get("/api/v1/droneDetails/{id}", 9999))
                    .andExpect(status().isNotFound());
        }
    }
}