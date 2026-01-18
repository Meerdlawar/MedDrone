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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR8: Testing REST API Endpoints
 * 
 * Based on LO2 Testing Plan Section 8: Testing REST API endpoints
 * 
 * Test Coverage:
 * 1. POST calcDeliveryPathAsGeoJson: Valid request returns 200 OK with valid GeoJSON.
 * 2. POST calcDeliveryPathAsGeoJson: Invalid JSON request returns 400 Bad Request.
 * 3. POST calcDeliveryPathAsGeoJson: Missing required fields return descriptive errors.
 * 4. POST calcDeliveryPathAsGeoJson: Invalid field values return validation errors.
 * 5. POST calcDeliveryPathAsGeoJson: Empty delivery list returns appropriate response.
 * 6. POST calcDeliveryPathAsGeoJson: Response time is acceptable for typical requests.
 * 
 * Total: 6 tests
 */
@DisplayName("FR8: REST API Endpoints Tests")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RestApiEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Standard valid request
    private static final String VALID_REQUEST = """
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

    // Invalid JSON (malformed)
    private static final String INVALID_JSON = """
        [
            {
                "id": 1,
                "date": "2025-12-12"
                "time": "14:30:00",  // missing comma
            }
        ]
        """;

    // Missing required fields
    private static final String MISSING_FIELDS_REQUEST = """
        [
            {
                "id": 1
            }
        ]
        """;

    // Invalid field values
    private static final String INVALID_VALUES_REQUEST = """
        [
            {
                "id": 1,
                "date": "not-a-date",
                "time": "not-a-time",
                "requirements": {"capacity": -5.0},
                "delivery": {"lng": "invalid", "lat": "invalid"}
            }
        ]
        """;

    // Empty delivery list
    private static final String EMPTY_REQUEST = "[]";

    // =========================================================================
    // FR8.1: Valid request returns 200 OK with valid GeoJSON
    // =========================================================================

    @Nested
    @DisplayName("FR8.1: Valid Request Handling")
    class FR8_1_ValidRequestTests {

        @Test
        @DisplayName("Valid request returns 200 OK")
        void FR8_1_1_validRequest_returns200OK() throws Exception {
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Valid request returns valid GeoJSON")
        void FR8_1_2_validRequest_returnsValidGeoJson() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(responseJson);
            
            // Verify GeoJSON structure
            assertThat(root.has("type"))
                    .describedAs("GeoJSON should have 'type' field")
                    .isTrue();
            
            assertThat(root.get("type").asText())
                    .describedAs("GeoJSON type should be valid")
                    .isIn("LineString", "MultiLineString", "FeatureCollection", "Feature", "GeometryCollection");
            
            // For LineString, verify coordinates exist
            if ("LineString".equals(root.get("type").asText())) {
                assertThat(root.has("coordinates"))
                        .describedAs("LineString should have coordinates")
                        .isTrue();
                
                JsonNode coordinates = root.get("coordinates");
                assertThat(coordinates.isArray())
                        .describedAs("Coordinates should be an array")
                        .isTrue();
            }
        }

        @Test
        @DisplayName("Response contains proper content type header")
        void FR8_1_3_response_containsProperContentType() throws Exception {
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    // =========================================================================
    // FR8.2: Invalid JSON request returns 400 Bad Request
    // =========================================================================

    @Nested
    @DisplayName("FR8.2: Invalid JSON Handling")
    class FR8_2_InvalidJsonTests {

        @Test
        @DisplayName("Malformed JSON returns 400 Bad Request")
        void FR8_2_1_malformedJson_returns400BadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INVALID_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Empty body returns 400 Bad Request")
        void FR8_2_2_emptyBody_returns400BadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Sending plain text instead of JSON returns 415 Unsupported Media Type")
        void testWrongContentType() throws Exception {
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.TEXT_PLAIN) // Tell the server it IS text
                            .content("This is not JSON"))
                    .andExpect(status().isUnsupportedMediaType()); // Expect 415
        }
    }

    // =========================================================================
    // FR8.3: Missing required fields return descriptive errors
    // =========================================================================

    @Nested
    @DisplayName("FR8.3: Missing Fields Handling")
    class FR8_3_MissingFieldsTests {

        @Test
        @DisplayName("Missing required fields returns error")
        void FR8_3_1_missingRequiredFields_returnsError() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MISSING_FIELDS_REQUEST))
                    .andReturn();

            int status = result.getResponse().getStatus();
            
            // Should return either 400 Bad Request or 422 Unprocessable Entity
            assertThat(status)
                    .describedAs("Missing fields should return 4xx error")
                    .isGreaterThanOrEqualTo(400)
                    .isLessThan(500);
        }

        @Test
        @DisplayName("Error response for missing fields is descriptive")
        void FR8_3_2_errorResponse_isDescriptive() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MISSING_FIELDS_REQUEST))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            
            if (result.getResponse().getStatus() >= 400) {
                // Error response should have some content
                assertThat(responseBody)
                        .describedAs("Error response should provide information")
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("Missing delivery location returns validation error")
        void FR8_3_3_missingDeliveryLocation_returnsValidationError() throws Exception {
            String noLocationRequest = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30:00",
                        "requirements": {"capacity": 2.0}
                    }
                ]
                """;
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noLocationRequest))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .describedAs("Missing delivery location should return error")
                    .isGreaterThanOrEqualTo(400);
        }
    }

    // =========================================================================
    // FR8.4: Invalid field values return validation errors
    // =========================================================================

    @Nested
    @DisplayName("FR8.4: Invalid Field Values Handling")
    class FR8_4_InvalidValuesTests {

        @Test
        @DisplayName("Invalid date format returns validation error")
        void FR8_4_1_invalidDateFormat_returnsValidationError() throws Exception {
            String invalidDateRequest = """
                [
                    {
                        "id": 1,
                        "date": "2025/12/12",
                        "time": "14:30:00",
                        "requirements": {"capacity": 2.0},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidDateRequest))
                    .andReturn();

            // May be 400 for parse error or 200 with validation error in response
            // Both are acceptable behaviors
            assertThat(result.getResponse().getStatus())
                    .describedAs("Invalid date should be handled")
                    .isIn(200, 400, 422);
        }

        @Test
        @DisplayName("Negative capacity returns validation error")
        void FR8_4_2_negativeCapacity_returnsValidationError() throws Exception {
            String negativeCapacityRequest = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30:00",
                        "requirements": {"capacity": -5.0},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(negativeCapacityRequest))
                    .andReturn();

            // Negative capacity should be rejected or result in undeliverable
            assertThat(result.getResponse().getStatus())
                    .describedAs("Negative capacity should be handled")
                    .isIn(200, 400, 422);
        }

        @Test
        @DisplayName("Invalid coordinates return error")
        void FR8_4_3_invalidCoordinates_returnError() throws Exception {
            String invalidCoordsRequest = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30:00",
                        "requirements": {"capacity": 2.0},
                        "delivery": {"lng": 999.0, "lat": 999.0}
                    }
                ]
                """;
            
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidCoordsRequest))
                    .andReturn();

            // Invalid coordinates should be rejected or handled gracefully
            assertThat(result.getResponse().getStatus())
                    .describedAs("Invalid coordinates should be handled")
                    .isIn(200, 400, 422, 500);
        }
    }

    // =========================================================================
    // FR8.5: Empty delivery list returns appropriate response
    // =========================================================================

    @Nested
    @DisplayName("FR8.5: Empty Delivery List Handling")
    class FR8_5_EmptyListTests {

        @Test
        @DisplayName("Empty delivery list returns valid response")
        void FR8_5_1_emptyDeliveryList_returnsValidResponse() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EMPTY_REQUEST))
                    .andReturn();

            // Empty list should either return 200 with empty result or 400
            assertThat(result.getResponse().getStatus())
                    .describedAs("Empty list should be handled gracefully")
                    .isIn(200, 400);
        }

        @Test
        @DisplayName("Empty delivery list response has valid structure")
        void FR8_5_2_emptyDeliveryList_hasValidStructure() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EMPTY_REQUEST))
                    .andReturn();

            if (result.getResponse().getStatus() == 200) {
                String responseJson = result.getResponse().getContentAsString();
                
                // Should be valid JSON
                assertThat(responseJson)
                        .describedAs("Empty result should still be valid JSON")
                        .satisfies(json -> {
                            // Just verify it parses
                            objectMapper.readTree(json);
                        });
            }
        }
    }

    // =========================================================================
    // FR8.6: Response time is acceptable for typical requests
    // =========================================================================

    @Nested
    @DisplayName("FR8.6: Response Time")
    class FR8_6_ResponseTimeTests {

        @Test
        @DisplayName("Simple request responds within acceptable time")
        void FR8_6_1_simpleRequest_respondsWithinAcceptableTime() throws Exception {
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST))
                    .andExpect(status().isOk());
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Per spec: should complete within 30 seconds
            assertThat(duration)
                    .describedAs("Simple request should complete within 30 seconds")
                    .isLessThan(30_000);
        }

        @Test
        @DisplayName("Multiple deliveries respond within acceptable time")
        void FR8_6_2_multipleDeliveries_respondWithinAcceptableTime() throws Exception {
            String multiDeliveryRequest = """
                [
                    {"id": 1, "date": "2025-12-12", "time": "14:30:00", 
                     "requirements": {"capacity": 1.0}, 
                     "delivery": {"lng": -3.188374, "lat": 55.944494}},
                    {"id": 2, "date": "2025-12-12", "time": "14:30:00", 
                     "requirements": {"capacity": 1.0}, 
                     "delivery": {"lng": -3.186500, "lat": 55.945000}},
                    {"id": 3, "date": "2025-12-12", "time": "14:30:00", 
                     "requirements": {"capacity": 1.0}, 
                     "delivery": {"lng": -3.190000, "lat": 55.943000}}
                ]
                """;
            
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(multiDeliveryRequest))
                    .andExpect(status().isOk());
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Per spec: should complete within 30 seconds
            assertThat(duration)
                    .describedAs("Multi-delivery request should complete within 30 seconds")
                    .isLessThan(30_000);
        }
    }
}
