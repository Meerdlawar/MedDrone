package uk.ac.ed.acp.cw2.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ed.acp.cw2.data.LngLat;
import uk.ac.ed.acp.cw2.data.PosOnePosTwo;
import uk.ac.ed.acp.cw2.data.NextPosition;
import uk.ac.ed.acp.cw2.data.Region;
import uk.ac.ed.acp.cw2.data.PositionRegion;
import uk.ac.ed.acp.cw2.data.IsInRegion;
import uk.ac.ed.acp.cw2.services.DroneNavigation;
import uk.ac.ed.acp.cw2.services.PointInRegion;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(DroneController.class)
@TestPropertySource(properties = { "ilp.service.url=http://localhost:8080" })
class DroneControllerWebTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void distanceTo_isMapped_and_usesStatic() throws Exception {
        LngLat p1 = new LngLat(-3.19, 55.94);
        LngLat p2 = new LngLat(-3.18, 55.95);
        PosOnePosTwo body = new PosOnePosTwo(p1, p2);

        try (MockedStatic<DroneNavigation> mocked = Mockito.mockStatic(DroneNavigation.class)) {
            mocked.when(() -> DroneNavigation.distance(eq(p1), eq(p2))).thenReturn(42.0);

            mvc.perform(post("/api/v1/distanceTo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("42.0"));

            mocked.verify(() -> DroneNavigation.distance(eq(p1), eq(p2)));
        }
    }

    @Test
    void isCloseTo_isMapped_and_usesStatic() throws Exception {
        LngLat p1 = new LngLat(-3.19, 55.94);
        LngLat p2 = new LngLat(-3.19, 55.9401);
        PosOnePosTwo body = new PosOnePosTwo(p1, p2);

        try (MockedStatic<DroneNavigation> mocked = Mockito.mockStatic(DroneNavigation.class)) {
            mocked.when(() -> DroneNavigation.isClose(eq(p1), eq(p2))).thenReturn(true);

            mvc.perform(post("/api/v1/isCloseTo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));

            mocked.verify(() -> DroneNavigation.isClose(eq(p1), eq(p2)));
        }
    }

    @Test
    void nextPosition_happyPath_computesFromAngle() throws Exception {
        // No mocking needed here; we use the real enum for a slice test.
        LngLat start = new LngLat(0.0, 0.0);
        // 0° should step due East by STEP_SIZE
        NextPosition body = new NextPosition(start, 0.0);

        mvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lng").value(org.hamcrest.number.IsCloseTo.closeTo(uk.ac.ed.acp.cw2.services.DroneNavigation.STEP_SIZE, 1e-12)))
                .andExpect(jsonPath("$.lat").value(org.hamcrest.number.IsCloseTo.closeTo(0.0, 1e-12)));
    }

    @Test
    void isInRegion_isMapped_and_usesStatic() throws Exception {
        // Square around the origin
        Region square = new Region("square", java.util.List.of(
                new PositionRegion(-1.0, -1.0),
                new PositionRegion( 1.0, -1.0),
                new PositionRegion( 1.0,  1.0),
                new PositionRegion(-1.0,  1.0)
        ));
        IsInRegion body = new IsInRegion(new PositionRegion(0.0, 0.0), square);

        try (MockedStatic<PointInRegion> mocked = Mockito.mockStatic(PointInRegion.class)) {
            mocked.when(() -> PointInRegion.isInRegion(any(LngLat.class), any(java.util.List.class)))
                    .thenReturn(true);

            mvc.perform(post("/api/v1/isInRegion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));

            mocked.verify(() -> PointInRegion.isInRegion(any(LngLat.class), any(java.util.List.class)));
        }
    }

    @Test
    void isInRegion_validationError_forSmallPolygon() throws Exception {
        // Only 3 vertices → violates @Size(min=4)
        Region tri = new Region("tri", java.util.List.of(
                new PositionRegion(0.0, 0.0),
                new PositionRegion(1.0, 0.0),
                new PositionRegion(0.0, 1.0)
        ));
        IsInRegion body = new IsInRegion(new PositionRegion(0.1, 0.1), tri);

        mvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void index_and_uid_endpoints_work() throws Exception {
        mvc.perform(get("/api/v1/"))
                .andExpect(status().isOk());


        mvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andExpect(content().string("s2532596"));
    }
}