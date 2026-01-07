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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DroneDynamicQueryController.
 * Tests queryAsPath (GET) and query (POST) endpoints.
 * 
 * These tests validate the exact behavior expected by the submission checker.
 * Note: These tests require network access to the ILP REST service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("DroneDynamicQueryController Integration Tests")
class DroneDynamicQueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // Query As Path Tests (QueryAsPathCommand)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/queryAsPath/{attributeName}/{attributeValue}")
    class QueryAsPathTests {

        @Test
        @DisplayName("Query for capacity=8 - returns [2,4,7,9]")
        void queryAsPath_capacity8_returnsExpectedIds() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "capacity", "8"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$", containsInAnyOrder(2, 4, 7, 9)));
        }

        @Test
        @DisplayName("Query for maxMoves=1500 - returns [5,10,99998,888,456]")
        void queryAsPath_maxMoves1500_returnsExpectedIds() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "maxMoves", "1500"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$", containsInAnyOrder(5, 10, 99998, 888, 456)));
        }

        @Test
        @DisplayName("Query for costPerMove=0.07 - returns [10,99998,888,456]")
        void queryAsPath_costPerMove007_returnsExpectedIds() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "costPerMove", "0.07"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$", containsInAnyOrder(10, 99998, 888, 456)));
        }

        @Test
        @DisplayName("Query for cooling=true - returns array of drone IDs")
        void queryAsPath_coolingTrue_returnsArray() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "cooling", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query for heating=true - returns array of drone IDs")
        void queryAsPath_heatingTrue_returnsArray() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "heating", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query for costInitial - returns array of drone IDs")
        void queryAsPath_costInitial_returnsArray() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "costInitial", "1.4"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query for costFinal - returns array of drone IDs")
        void queryAsPath_costFinal_returnsArray() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "costFinal", "3.5"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // Query (POST) Tests (QueryAsPostCommand)
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/query")
    class QueryPostTests {

        @Test
        @DisplayName("Query for costPerMove < 0.04 AND maxMoves > 1000 - returns [1,6]")
        void query_costPerMoveAndMaxMoves_returnsExpectedIds() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"costPerMove","operator":"<","value":"0.04"},
                        {"attribute":"maxMoves","operator":">","value":"1000"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$", containsInAnyOrder(1, 6)));
        }

        @Test
        @DisplayName("Query for costFinal=3.5 AND maxMoves=1500 - returns [5,10,99998,888,456]")
        void query_costFinalAndMaxMoves_returnsExpectedIds() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"costFinal","operator":"=","value":"3.5"},
                        {"attribute":"maxMoves","operator":"=","value":"1500"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$", containsInAnyOrder(5, 10, 99998, 888, 456)));
        }

        @Test
        @DisplayName("Query for maxMoves=3333 (no match) - returns empty array")
        void query_maxMovesNoResult_returnsEmptyArray() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"maxMoves","operator":"=","value":"3333"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Query with single equals condition - returns matching drones")
        void query_singleEqualsCondition_returnsMatching() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"capacity","operator":"=","value":"8"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$", containsInAnyOrder(2, 4, 7, 9)));
        }

        @Test
        @DisplayName("Query with greater than operator - returns matching drones")
        void query_greaterThanOperator_returnsMatching() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"capacity","operator":">","value":"10"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with less than operator - returns matching drones")
        void query_lessThanOperator_returnsMatching() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"costPerMove","operator":"<","value":"0.05"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with boolean cooling=true - returns matching drones")
        void query_booleanCoolingTrue_returnsMatching() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"cooling","operator":"=","value":"true"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("Query with multiple conditions - returns intersection")
        void query_multipleConditions_returnsIntersection() throws Exception {
            String requestBody = """
                    [
                        {"attribute":"cooling","operator":"=","value":"true"},
                        {"attribute":"capacity","operator":">","value":"5"}
                    ]
                    """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with empty array - returns all drones or handles gracefully")
        void query_emptyArray_handlesGracefully() throws Exception {
            String requestBody = "[]";

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
