package uk.ac.ed.acp.cw2.controller; // <- change to your actual test package

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ed.acp.cw2.data.*;
import uk.ac.ed.acp.cw2.services.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.services.DroneQueryService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Mock MVC tests for CW2 endpoints only.
 *
 * Assumes endpoints (from spec) like:
 *  - GET  /api/v1/dronesWithCooling/{state}
 *  - GET  /api/v1/droneDetails/{id}
 *  - GET  /api/v1/queryAsPath/{attribute-name}/{attribute-value}
 *  - POST /api/v1/query
 *  - POST /api/v1/queryAvailableDrones
 *  - POST /api/v1/calcDeliveryPath
 *  - POST /api/v1/calcDeliveryPathAsGeoJson
 */
@WebMvcTest // (controllers = { YourCw2Controller.class })
class Cw2EndpointsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Use your real services, but mocked
    @MockitoBean
    private DroneQueryService droneQueryService;

    @MockitoBean
    private DroneAvailabilityService droneAvailabilityService;

    // Placeholder for your delivery planning bean (you can replace with real one)
    @MockitoBean
    private DeliveryPlanningService deliveryPlanningService;

    // -------------------------------------------------------------------------
    // 1) Static queries
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/dronesWithCooling/{state}")
    class DronesWithCoolingTests {

        @Test
        @DisplayName("returns drone IDs that support cooling when state=true")
        void dronesWithCooling_true_returnsIds() throws Exception {
            // GIVEN: all drones from ILP
            List<DroneInfo> drones = List.of(
                    new DroneInfo("Drone 1", 1, new DroneCapability(true, false, 4.0, 4000, 0.02, 2.0, 2.5)),
                    new DroneInfo("Drone 2", 2, new DroneCapability(false, false, 6.0, 5000, 0.03, 3.0, 3.5)),
                    new DroneInfo("Drone 4", 4, new DroneCapability(true, false, 8.0, 6000, 0.04, 4.0, 4.5)),
                    new DroneInfo("Drone 7", 7, new DroneCapability(true, false, 8.0, 6000, 0.04, 4.0, 4.5))
            );
            when(droneQueryService.fetchDrones()).thenReturn(drones);

            // WHEN + THEN
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0]").value(1))
                    .andExpect(jsonPath("$[1]").value(4))
                    .andExpect(jsonPath("$[2]").value(7));
        }

        @Test
        @DisplayName("returns drone IDs that do not support cooling when state=false")
        void dronesWithCooling_false_returnsIds() throws Exception {
            List<DroneInfo> drones = List.of(
                    new DroneInfo("Drone 1", 1, new DroneCapability(true, false, 4.0, 4000, 0.02, 2.0, 2.5)),
                    new DroneInfo("Drone 2", 2, new DroneCapability(false, false, 6.0, 5000, 0.03, 3.0, 3.5)),
                    new DroneInfo("Drone 3", 3, new DroneCapability(false, true, 8.0, 6000, 0.04, 4.0, 4.5))
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

    @Nested
    @DisplayName("GET /api/v1/droneDetails/{id}")
    class DroneDetailsTests {

        @Test
        @DisplayName("returns drone details for valid id (200)")
        void droneDetails_validId_returnsDrone() throws Exception {
            DroneInfo target = new DroneInfo(
                    "Drone 4",
                    4,
                    new DroneCapability(true, false, 8.0, 6000, 0.03, 3.4, 4.5)
            );
            List<DroneInfo> drones = List.of(
                    new DroneInfo("Drone 1", 1, new DroneCapability(false, false, 4.0, 4000, 0.02, 2.0, 2.5)),
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
                    new DroneInfo("Drone 1", 1, new DroneCapability(false, false, 4.0, 4000, 0.02, 2.0, 2.5))
            );
            when(droneQueryService.fetchDrones()).thenReturn(drones);

            mockMvc.perform(get("/api/v1/droneDetails/{id}", 9999))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // 2) Dynamic queries
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/queryAsPath/{attribute-name}/{attribute-value}")
    class QueryAsPathTests {

        @Test
        @DisplayName("capacity==8 returns matching drone IDs")
        void queryAsPath_capacityEquals8() throws Exception {
            // controller likely converts path into a single QueryAttributes and calls filterDroneAttributes
            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{3, 4});

            mockMvc.perform(get("/api/v1/queryAsPath/{attr}/{val}", "capacity", "8"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0]").value(3))
                    .andExpect(jsonPath("$[1]").value(4));
        }

        @Test
        @DisplayName("returns empty list when no drones match")
        void queryAsPath_noMatches_returnsEmptyList() throws Exception {
            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{});

            mockMvc.perform(get("/api/v1/queryAsPath/{attr}/{val}", "capacity", "999"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/query")
    class QueryPostTests {

        @Test
        @DisplayName("returns ids for complex query with multiple attributes (AND)")
        void queryPost_complexFilter_returnsIds() throws Exception {
            List<QueryAttributes> body = List.of(
                    new QueryAttributes("capacity", "<", "8"),
                    new QueryAttributes("cooling", "=", "true")
            );

            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{1, 5});

            mockMvc.perform(
                            post("/api/v1/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(body))
                    )
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0]").value(1))
                    .andExpect(jsonPath("$[1]").value(5));
        }

        @Test
        @DisplayName("returns empty list when dynamic query has no matches")
        void queryPost_noMatches_returnsEmpty() throws Exception {
            List<QueryAttributes> body = List.of(
                    new QueryAttributes("capacity", "<", "1")
            );

            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{});

            mockMvc.perform(
                            post("/api/v1/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(body))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // -------------------------------------------------------------------------
    // 3) Drone availability queries (POST /api/v1/queryAvailableDrones)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/queryAvailableDrones")
    class QueryAvailableDronesTests {

        @Test
        @DisplayName("returns drone ids that can satisfy ALL MedDispatchRec records")
        void queryAvailableDrones_withAvailability_returnsIds() throws Exception {
            List<MedDispatchRec> dispatches = List.of(
                    new MedDispatchRec(
                            101,
                            LocalDate.of(2025, 12, 22),
                            LocalTime.of(14, 30),
                            new DispatchRequirements(0.75, false, true, 13.5),
                            new LngLat(-3.1863580788986368, 55.94468066708487)
                    ),
                    new MedDispatchRec(
                            102,
                            LocalDate.of(2025, 12, 22),
                            LocalTime.of(15, 40),
                            new DispatchRequirements(1.0, false, true, 20.0),
                            new LngLat(-3.1863, 55.9446)
                    )
            );

            when(droneAvailabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{2, 4});

            mockMvc.perform(
                            post("/api/v1/queryAvailableDrones")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dispatches))
                    )
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0]").value(2))
                    .andExpect(jsonPath("$[1]").value(4));
        }

        @Test
        @DisplayName("returns empty list when no single drone can serve all dispatches")
        void queryAvailableDrones_noAvailability_returnsEmpty() throws Exception {
            List<MedDispatchRec> dispatches = List.of(
                    new MedDispatchRec(
                            201,
                            LocalDate.of(2025, 12, 22),
                            LocalTime.of(10, 0),
                            new DispatchRequirements(20.0, true, false, null),
                            new LngLat(-3.19, 55.94)
                    )
            );

            when(droneAvailabilityService.queryAvailableDrones(anyList()))
                    .thenReturn(new int[]{});

            mockMvc.perform(
                            post("/api/v1/queryAvailableDrones")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dispatches))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // -------------------------------------------------------------------------
    // 4) calcDeliveryPath (POST /api/v1/calcDeliveryPath)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/calcDeliveryPath")
    class CalcDeliveryPathTests {

        @Test
        @DisplayName("returns delivery plan with cost, moves and drone paths")
        void calcDeliveryPath_simpleCase() throws Exception {
            List<MedDispatchRec> dispatches = List.of(
                    new MedDispatchRec(
                            301,
                            LocalDate.of(2025, 12, 22),
                            LocalTime.of(14, 30),
                            new DispatchRequirements(0.75, false, true, 30.0),
                            new LngLat(-3.18635, 55.94468)
                    ),
                    new MedDispatchRec(
                            302,
                            LocalDate.of(2025, 12, 22),
                            LocalTime.of(15, 0),
                            new DispatchRequirements(0.50, false, true, 30.0),
                            new LngLat(-3.1864, 55.9447)
                    )
            );

            DeliveryPlan plan = new DeliveryPlan(
                    1234.44,
                    12111,
                    List.of(
                            new DronePath(
                                    4,
                                    List.of(
                                            new DeliveryPath(
                                                    301,
                                                    List.of(
                                                            new LngLat(-3.1863580788986368, 55.94468066708487),
                                                            new LngLat(-3.186359, 55.94468066708487)
                                                    )
                                            )
                                    )
                            )
                    )
            );

            when(deliveryPlanningService.calculateDeliveryPath(anyList())).thenReturn(plan);

            mockMvc.perform(
                            post("/api/v1/calcDeliveryPath")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dispatches))
                    )
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.totalCost").value(1234.44))
                    .andExpect(jsonPath("$.totalMoves").value(12111))
                    .andExpect(jsonPath("$.dronePaths[0].droneId").value(4))
                    .andExpect(jsonPath("$.dronePaths[0].deliveries[0].deliveryId").value(301));
        }

        @Test
        @DisplayName("returns empty plan when nothing is deliverable (still 200)")
        void calcDeliveryPath_notResolvable_returnsEmptyPlan() throws Exception {
            List<MedDispatchRec> dispatches = List.of(
                    new MedDispatchRec(
                            999,
                            LocalDate.of(2025, 12, 22),
                            LocalTime.of(23, 59),
                            new DispatchRequirements(999.0, true, false, 1.0),
                            new LngLat(-3.2, 55.94)
                    )
            );

            DeliveryPlan emptyPlan = new DeliveryPlan(0.0, 0, List.of());

            when(deliveryPlanningService.calculateDeliveryPath(anyList())).thenReturn(emptyPlan);

            mockMvc.perform(
                            post("/api/v1/calcDeliveryPath")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dispatches))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(0.0))
                    .andExpect(jsonPath("$.totalMoves").value(0))
                    .andExpect(jsonPath("$.dronePaths.length()").value(0));
        }
    }

    // -------------------------------------------------------------------------
    // 5) calcDeliveryPathAsGeoJson (POST /api/v1/calcDeliveryPathAsGeoJson)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/calcDeliveryPathAsGeoJson")
    class CalcDeliveryPathAsGeoJsonTests {

        @Test
        @DisplayName("returns valid GeoJSON LineString")
        void calcDeliveryPathAsGeoJson_returnsGeoJson() throws Exception {
            List<MedDispatchRec> dispatches = List.of(
                    new MedDispatchRec(
                            401,
                            LocalDate.of(2025, 12, 22),
                            LocalTime.of(14, 30),
                            new DispatchRequirements(0.75, false, true, 50.0),
                            new LngLat(-3.18635, 55.94468)
                    )
            );

            String geoJson = """
                    {
                      "type": "LineString",
                      "coordinates": [
                        [-3.1863580788986368, 55.94468066708487],
                        [-3.186359, 55.94468066708487]
                      ]
                    }
                    """;

            when(deliveryPlanningService.calculateDeliveryPathAsGeoJson(anyList()))
                    .thenReturn(geoJson);

            mockMvc.perform(
                            post("/api/v1/calcDeliveryPathAsGeoJson")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dispatches))
                    )
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.type").value("LineString"))
                    .andExpect(jsonPath("$.coordinates.length()").value(2));
        }
    }

    // -------------------------------------------------------------------------
    // Scaffolding for testing
    // -------------------------------------------------------------------------

    interface DeliveryPlanningService {
        DeliveryPlan calculateDeliveryPath(List<MedDispatchRec> dispatches);

        String calculateDeliveryPathAsGeoJson(List<MedDispatchRec> dispatches);
    }
}