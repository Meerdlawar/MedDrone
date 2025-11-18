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
import uk.ac.ed.acp.cw2.data.DispatchRequirements;
import uk.ac.ed.acp.cw2.data.LngLat;
import uk.ac.ed.acp.cw2.data.MedDispatchRec;
import uk.ac.ed.acp.cw2.services.DroneAvailabilityService;
import uk.ac.ed.acp.cw2.services.DroneQueryService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(controllers = DroneDynamicQueryController.class)
class DroneDynamicQueryControllerTest{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock your *real* services
    @MockitoBean
    private DroneQueryService droneQueryService;

    @MockitoBean
    private DroneAvailabilityService droneAvailabilityService;

    // -------------------------------------------------------------------------
    // 4) /queryAvailableDrones (uses DroneAvailabilityService)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/queryAvailableDrones")
    class QueryAvailableDronesTests {

        @Test
        @DisplayName("returns drone IDs that can satisfy ALL dispatches")
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
}