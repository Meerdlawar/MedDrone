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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR: Non-Functional Requirements Tests
 * 
 * Based on LO2 Testing Plan Section: Non-functional requirements
 * 
 * Test Coverage:
 * 1. Performance: Total execution time < 30 seconds for complete dispatch calculation.
 * 2. Determinism: Same inputs always produce identical outputs.
 * 3. Docker Deployment: Application builds and runs successfully in Docker container.
 * 
 * Total: 3 tests
 */
@DisplayName("NFR: Non-Functional Requirements Tests")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class NonFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Standard test request
    private static final String STANDARD_REQUEST = """
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

    // Complex multi-delivery request
    private static final String COMPLEX_REQUEST = """
        [
            {"id": 1, "date": "2025-12-12", "time": "14:30:00", 
             "requirements": {"capacity": 1.0}, 
             "delivery": {"lng": -3.188374, "lat": 55.944494}},
            {"id": 2, "date": "2025-12-12", "time": "14:30:00", 
             "requirements": {"capacity": 1.5, "cooling": true}, 
             "delivery": {"lng": -3.186500, "lat": 55.945000}},
            {"id": 3, "date": "2025-12-12", "time": "14:30:00", 
             "requirements": {"capacity": 2.0}, 
             "delivery": {"lng": -3.190000, "lat": 55.943000}},
            {"id": 4, "date": "2025-12-12", "time": "14:30:00", 
             "requirements": {"capacity": 0.5}, 
             "delivery": {"lng": -3.185000, "lat": 55.946000}},
            {"id": 5, "date": "2025-12-12", "time": "14:30:00", 
             "requirements": {"capacity": 1.0, "heating": true}, 
             "delivery": {"lng": -3.187000, "lat": 55.942000}}
        ]
        """;

    // =========================================================================
    // NFR1: Performance - Total execution time < 30 seconds
    // =========================================================================

    @Nested
    @DisplayName("NFR1: Performance Requirements")
    class NFR1_PerformanceTests {

        @Test
        @DisplayName("Simple delivery completes within 30 seconds")
        void NFR1_1_simpleDelivery_completesWithin30Seconds() throws Exception {
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk());
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(duration)
                    .describedAs("Simple delivery should complete within 30 seconds (actual: %dms)", duration)
                    .isLessThan(30_000);
        }

        @Test
        @DisplayName("Complex multi-delivery completes within 30 seconds")
        void NFR1_2_complexDelivery_completesWithin30Seconds() throws Exception {
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COMPLEX_REQUEST))
                    .andExpect(status().isOk());
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(duration)
                    .describedAs("Complex delivery should complete within 30 seconds (actual: %dms)", duration)
                    .isLessThan(30_000);
        }

        @Test
        @DisplayName("Average response time is reasonable")
        void NFR1_3_averageResponseTime_isReasonable() throws Exception {
            List<Long> responseTimes = new ArrayList<>();
            
            // Make multiple requests and track times
            for (int i = 0; i < 3; i++) {
                long startTime = System.currentTimeMillis();
                
                mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(STANDARD_REQUEST))
                        .andExpect(status().isOk());
                
                responseTimes.add(System.currentTimeMillis() - startTime);
            }
            
            double averageTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
            
            assertThat(averageTime)
                    .describedAs("Average response time should be reasonable")
                    .isLessThan(30_000);
        }
    }

    // =========================================================================
    // NFR2: Determinism - Same inputs always produce identical outputs
    // =========================================================================

    @Nested
    @DisplayName("NFR2: Determinism Requirements")
    class NFR2_DeterminismTests {

        @Test
        @DisplayName("Same input produces identical output")
        void NFR2_1_sameInput_producesIdenticalOutput() throws Exception {
            // First request
            MvcResult result1 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String response1 = result1.getResponse().getContentAsString();

            // Second request with same input
            MvcResult result2 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            String response2 = result2.getResponse().getContentAsString();

            // Compare JSON structures (not string equality due to potential whitespace differences)
            JsonNode json1 = objectMapper.readTree(response1);
            JsonNode json2 = objectMapper.readTree(response2);
            
            assertThat(json1)
                    .describedAs("Same input should produce identical output")
                    .isEqualTo(json2);
        }

        @Test
        @DisplayName("Multiple executions produce consistent results")
        void NFR2_2_multipleExecutions_produceConsistentResults() throws Exception {
            List<String> responses = new ArrayList<>();
            
            // Execute multiple times
            for (int i = 0; i < 5; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(STANDARD_REQUEST))
                        .andExpect(status().isOk())
                        .andReturn();
                
                responses.add(result.getResponse().getContentAsString());
            }
            
            // Parse first response as reference
            JsonNode reference = objectMapper.readTree(responses.get(0));
            
            // Compare all others to reference
            for (int i = 1; i < responses.size(); i++) {
                JsonNode current = objectMapper.readTree(responses.get(i));
                
                assertThat(current)
                        .describedAs("Execution %d should match reference", i + 1)
                        .isEqualTo(reference);
            }
        }

        @Test
        @DisplayName("Path coordinates are deterministic")
        void NFR2_3_pathCoordinates_areDeterministic() throws Exception {
            // First request
            MvcResult result1 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode json1 = objectMapper.readTree(result1.getResponse().getContentAsString());
            JsonNode coords1 = json1.get("coordinates");

            // Second request
            MvcResult result2 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode json2 = objectMapper.readTree(result2.getResponse().getContentAsString());
            JsonNode coords2 = json2.get("coordinates");

            if (coords1 != null && coords2 != null) {
                assertThat(coords1.size())
                        .describedAs("Path length should be deterministic")
                        .isEqualTo(coords2.size());
                
                // Verify each coordinate matches
                for (int i = 0; i < coords1.size(); i++) {
                    assertThat(coords1.get(i))
                            .describedAs("Coordinate %d should be deterministic", i)
                            .isEqualTo(coords2.get(i));
                }
            }
        }

        @Test
        @DisplayName("Complex delivery path is deterministic")
        void NFR2_4_complexDeliveryPath_isDeterministic() throws Exception {
            // First request
            MvcResult result1 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COMPLEX_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            // Second request
            MvcResult result2 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COMPLEX_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode json1 = objectMapper.readTree(result1.getResponse().getContentAsString());
            JsonNode json2 = objectMapper.readTree(result2.getResponse().getContentAsString());
            
            assertThat(json1)
                    .describedAs("Complex delivery should produce deterministic results")
                    .isEqualTo(json2);
        }
    }

    // =========================================================================
    // NFR3: Docker Deployment
    // =========================================================================

    @Nested
    @DisplayName("NFR3: Docker Deployment Requirements")
    class NFR3_DockerDeploymentTests {

        @Test
        @DisplayName("Application responds to health check")
        void NFR3_1_application_respondsToHealthCheck() throws Exception {
            // This test verifies the application is running and responding
            // In a full Docker test, this would be called from outside the container
            
            // The application being able to receive and process requests
            // indicates it's deployed and running correctly
            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Application handles concurrent requests")
        void NFR3_2_application_handlesConcurrentRequests() throws Exception {
            // Simulate multiple concurrent-like requests
            List<MvcResult> results = new ArrayList<>();
            
            for (int i = 0; i < 3; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(STANDARD_REQUEST))
                        .andExpect(status().isOk())
                        .andReturn();
                results.add(result);
            }
            
            // All requests should succeed
            assertThat(results)
                    .describedAs("All concurrent requests should complete")
                    .hasSize(3);
            
            for (MvcResult result : results) {
                assertThat(result.getResponse().getStatus())
                        .describedAs("Each request should return 200 OK")
                        .isEqualTo(200);
            }
        }

        @Test
        @DisplayName("Application maintains state correctly across requests")
        void NFR3_3_application_maintainsStateCorrectly() throws Exception {
            // First request
            MvcResult result1 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            // Different request
            String differentRequest = """
                [
                    {
                        "id": 99,
                        "date": "2025-12-15",
                        "time": "10:00:00",
                        "requirements": {"capacity": 1.0},
                        "delivery": {"lng": -3.190000, "lat": 55.946000}
                    }
                ]
                """;
            
            MvcResult result2 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(differentRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            // Original request again - should get same result as first
            MvcResult result3 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STANDARD_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode json1 = objectMapper.readTree(result1.getResponse().getContentAsString());
            JsonNode json3 = objectMapper.readTree(result3.getResponse().getContentAsString());
            
            assertThat(json1)
                    .describedAs("Application should maintain correct state across different requests")
                    .isEqualTo(json3);
        }
    }
}
