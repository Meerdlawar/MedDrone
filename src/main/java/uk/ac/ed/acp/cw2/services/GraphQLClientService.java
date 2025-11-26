package uk.ac.ed.acp.cw2.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * OPTIMIZED: Internal GraphQL client for executing GraphQL queries
 * This allows internal services to use GraphQL queries instead of multiple REST calls,
 * reducing overfetching and consolidating data access logic.
 */
@Service
public class GraphQLClientService {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLClientService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GraphQLClientService(
            @Value("${server.port:8080}") String serverPort,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:" + serverPort + "/graphql")
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Execute a GraphQL query and return the raw JSON response
     *
     * @param query The GraphQL query string
     * @return JsonNode containing the response data
     */
    public JsonNode executeQuery(String query) {
        return executeQuery(query, null);
    }

    /**
     * Execute a GraphQL query with variables
     *
     * @param query The GraphQL query string
     * @param variables Map of variable names to values
     * @return JsonNode containing the response data
     */
    public JsonNode executeQuery(String query, Map<String, Object> variables) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            if (variables != null && !variables.isEmpty()) {
                requestBody.put("variables", variables);
            }

            logger.debug("Executing GraphQL query: {}", query);

            String response = restClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode jsonResponse = objectMapper.readTree(response);

            // Check for errors
            if (jsonResponse.has("errors")) {
                logger.error("GraphQL query returned errors: {}", jsonResponse.get("errors"));
                throw new RuntimeException("GraphQL query failed: " + jsonResponse.get("errors"));
            }

            return jsonResponse.get("data");

        } catch (Exception e) {
            logger.error("Failed to execute GraphQL query", e);
            throw new RuntimeException("GraphQL query execution failed", e);
        }
    }

    /**
     * Execute a GraphQL query and parse the response to a specific type
     *
     * @param query The GraphQL query string
     * @param resultPath Path to the data in the response (e.g., "drones")
     * @param targetType The class to deserialize the result into
     * @return Deserialized object of type T
     */
    public <T> T executeQueryAndParse(String query, String resultPath, Class<T> targetType) {
        try {
            JsonNode data = executeQuery(query);
            JsonNode result = data.get(resultPath);

            if (result == null) {
                logger.warn("No data found at path: {}", resultPath);
                return null;
            }

            return objectMapper.treeToValue(result, targetType);

        } catch (Exception e) {
            logger.error("Failed to parse GraphQL response", e);
            throw new RuntimeException("Failed to parse GraphQL response", e);
        }
    }
}
