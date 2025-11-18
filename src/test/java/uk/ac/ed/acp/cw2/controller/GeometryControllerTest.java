package uk.ac.ed.acp.cw2.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ed.acp.cw2.services.DroneNavigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(GeometryController.class)
class GeometryControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    DroneNavigation droneNavigation;

    // ---------- distanceTo ----------

    @Test
    void distanceTo_valid_returnsExpectedDistance() throws Exception {
        String body = """
            {
              "position1": { "lng": -3.192473, "lat": 55.946233 },
              "position2": { "lng": -3.192473, "lat": 55.942617 }
            }
            """;

        var result = mvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        double value = Double.parseDouble(result.getResponse().getContentAsString());
        // Expected ≈ 0.003616
        assertThat(value).isCloseTo(0.003616, within(1e-9));
    }

    @Test
    void distanceTo_semanticErrorCoords_stillReturns200AndDistance() throws Exception {
        String body = """
            {
              "position1": { "lng": -300.192473, "lat": 550.946233 },
              "position2": { "lng": -3202.192473, "lat": 5533.942617 }
            }
            """;

        var result = mvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        double value = Double.parseDouble(content);
        // Just assert it's a large positive distance (like the checker saw: ~5766)
        assertThat(value).isGreaterThan(1000.0);
    }

    @Test
    void distanceTo_syntaxErrorBody_returns400() throws Exception {
        // malformed JSON / wrong field names like in the checker example
        String body = """
            { "position1": { "lng": -3.192473, },
              "position2": { "lng": -3.192473, "lat_Pos2": 55.942617 } }
            """;

        mvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void distanceTo_emptyBody_returns400() throws Exception {
        mvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    // ---------- isCloseTo ----------

    @Test
    void isCloseTo_valid_returnsTrue() throws Exception {
        String body = """
            {
              "position1": { "lng": -3.192473, "lat": 55.946233 },
              "position2": { "lng": -3.192473, "lat": 55.946117 }
            }
            """;

        var result = mvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).isEqualTo("true");
    }

    @Test
    void isCloseTo_semanticErrorCoords_returns200AndFalse() throws Exception {
        String body = """
            {
              "position1": { "lng": -3004.192473, "lat": 550.946233 },
              "position2": { "lng": -390.192473, "lat": 551.942617 }
            }
            """;

        var result = mvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).isEqualTo("false");
    }

    @Test
    void isCloseTo_syntaxErrorBody_returns400() throws Exception {
        String body = """
            {
              "position1": { "lng": -3.192473, "lat": 55.946233 },
              "position3": { "lng": -3.192473, "lat": 55.942617 }
            }
            """;

        mvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isCloseTo_emptyBody_returns400() throws Exception {
        mvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    // ---------- nextPosition ----------

    @Test
    void nextPosition_valid_returnsNextStep() throws Exception {
        String body = """
            {
              "start": { "lng": -3.192473, "lat": 55.946233 },
              "angle": 90
            }
            """;

        var result = mvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Double> point =
                mapper.readValue(json, java.util.Map.class);

        double lng = point.get("lng");
        double lat = point.get("lat");


        assertThat(lng).isCloseTo(-3.192473, within(1e-9));
        assertThat(lat).isCloseTo(55.946383, within(1e-9));
    }

    @Test
    void nextPosition_semanticErrorAngle_returns400() throws Exception {
        // angle 900 is invalid, controller should send 400
        String body = """
            {
              "start": { "lng": -3.192473, "lat": 55.946233 },
              "angle": 900
            }
            """;

        mvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nextPosition_syntaxErrorBody_returns400() throws Exception {
        // startPosition instead of start, as in checker syntax error example
        String body = """
            {
              "startPosition": { "lng": -3.192473, "lat": 55.946233 },
              "angle": 90
            }
            """;

        mvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nextPosition_emptyBody_returns400() throws Exception {
        mvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    // ---------- isInRegion ----------

    @Test
    void isInRegion_valid_returnsTrue() throws Exception {
        String body = """
            {
              "position": { "lng": -3.186000, "lat": 55.944000 },
              "region": {
                "name": "central",
                "vertices": [
                  { "lng": -3.192473, "lat": 55.946233 },
                  { "lng": -3.192473, "lat": 55.942617 },
                  { "lng": -3.184319, "lat": 55.942617 },
                  { "lng": -3.184319, "lat": 55.946233 },
                  { "lng": -3.192473, "lat": 55.946233 }
                ]
              }
            }
            """;

        var result = mvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).isEqualTo("true");
    }

    @Test
    void isInRegion_semanticErrorCoordsOrOpenPolygon_returns400() throws Exception {
        // matches the "semantic error isInRegion" example: invalid coordinates / not properly closed
        String body = """
            {
              "position": { "lng": -390.186000, "lat": 550.944000 },
              "region": {
                "name": "central",
                "vertices": [
                  { "lng": -3.192473, "lat": 558.946233 },
                  { "lng": -367.192473, "lat": 55.942617 },
                  { "lng": -3.184319, "lat": 55.942617 },
                  { "lng": -3.184319, "lat": 55.946233 },
                  { "lng": -3.192473, "lat": 55.946233 }
                ]
              }
            }
            """;

        mvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isInRegion_syntaxErrorBody_returns400() throws Exception {
        // currentPosition, names, verticesList instead of position, name, vertices
        String body = """
            {
              "currentPosition": { "lng": 1.234, "lat": 1.222 },
              "region": {
                "names": "central",
                "verticesList": [
                  { "lng": -3.192473, "lat": 55.946233 },
                  { "lng": -3.192473, "lat": 55.942617 },
                  { "lng": -3.184319, "lat": 55.942617 },
                  { "lng": -3.184319, "lat": 55.946233 },
                  { "lng": -3.192473, "lat": 55.946233 }
                ]
              }
            }
            """;

        mvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isInRegion_emptyBody_returns400() throws Exception {
        mvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isInRegion_openPolygon_verticesTooShort_returns400() throws Exception {
        // only 3 vertices → not a valid closed polygon (matches "open vertices" example)
        String body = """
            {
              "position": { "lng": 398.234, "lat": 500.222 },
              "region": {
                "name": "central",
                "vertices": [
                  { "lng": -3.192473, "lat": 55.946233 },
                  { "lng": -3.192473, "lat": 55.942617 },
                  { "lng": -3.184319, "lat": 55.942617 }
                ]
              }
            }
            """;

        mvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // small helper for AssertJ within() to avoid static import noise
    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}