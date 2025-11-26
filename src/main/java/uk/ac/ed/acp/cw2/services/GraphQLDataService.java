package uk.ac.ed.acp.cw2.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.List;

/**
 * OPTIMIZED: Data service that uses GraphQL queries instead of multiple REST calls
 * This reduces overfetching by requesting only the fields needed
 * and consolidates multiple requests into single GraphQL queries
 */
@Service
public class GraphQLDataService {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLDataService.class);

    private final GraphQLClientService graphQLClient;
    private final ObjectMapper objectMapper;

    public GraphQLDataService(GraphQLClientService graphQLClient, ObjectMapper objectMapper) {
        this.graphQLClient = graphQLClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch all drones using GraphQL query
     * Only requests the fields actually needed by the application
     */
    @Cacheable("drones")
    public List<DroneInfo> fetchDrones() {
        logger.info("Fetching drones via GraphQL");

        String query = """
            {
              drones {
                id
                name
                capability {
                  cooling
                  heating
                  capacity
                  maxMoves
                  costPerMove
                  costInitial
                  costFinal
                }
              }
            }
            """;

        try {
            JsonNode data = graphQLClient.executeQuery(query);
            JsonNode dronesNode = data.get("drones");

            return objectMapper.convertValue(
                    dronesNode,
                    new TypeReference<List<DroneInfo>>() {}
            );
        } catch (Exception e) {
            logger.error("Failed to fetch drones via GraphQL", e);
            throw new RuntimeException("Failed to fetch drones", e);
        }
    }

    /**
     * Fetch service points using GraphQL query
     */
    @Cacheable("servicePoints")
    public List<ServicePoints> fetchServicePoints() {
        logger.info("Fetching service points via GraphQL");

        String query = """
            {
              servicePoints {
                id
                name
                location {
                  lng
                  lat
                }
              }
            }
            """;

        try {
            JsonNode data = graphQLClient.executeQuery(query);
            JsonNode servicePointsNode = data.get("servicePoints");

            return objectMapper.convertValue(
                    servicePointsNode,
                    new TypeReference<List<ServicePoints>>() {}
            );
        } catch (Exception e) {
            logger.error("Failed to fetch service points via GraphQL", e);
            throw new RuntimeException("Failed to fetch service points", e);
        }
    }

    /**
     * Fetch a single drone by ID using GraphQL query
     * This avoids fetching all drones when only one is needed
     */
    public DroneInfo fetchDroneById(int id) {
        logger.info("Fetching drone {} via GraphQL", id);

        String query = String.format("""
            {
              drone(id: %d) {
                id
                name
                capability {
                  cooling
                  heating
                  capacity
                  maxMoves
                  costPerMove
                  costInitial
                  costFinal
                }
              }
            }
            """, id);

        try {
            JsonNode data = graphQLClient.executeQuery(query);
            JsonNode droneNode = data.get("drone");

            if (droneNode == null || droneNode.isNull()) {
                return null;
            }

            return objectMapper.convertValue(droneNode, DroneInfo.class);
        } catch (Exception e) {
            logger.error("Failed to fetch drone {} via GraphQL", id, e);
            throw new RuntimeException("Failed to fetch drone", e);
        }
    }

    /**
     * Fetch drones with filters using GraphQL
     * This demonstrates the power of GraphQL - filtering on the server side
     */
    public List<DroneInfo> fetchDronesWithFilters(
            Boolean cooling,
            Boolean heating,
            Double minCapacity,
            Integer limit) {

        logger.info("Fetching filtered drones via GraphQL");

        StringBuilder filtersBuilder = new StringBuilder();
        boolean hasFilters = false;

        if (cooling != null || heating != null || minCapacity != null) {
            filtersBuilder.append("filters: {");

            if (cooling != null || heating != null || minCapacity != null) {
                filtersBuilder.append(" capability: {");
                if (cooling != null) {
                    filtersBuilder.append(" cooling: ").append(cooling);
                }
                if (heating != null) {
                    if (cooling != null) filtersBuilder.append(",");
                    filtersBuilder.append(" heating: ").append(heating);
                }
                if (minCapacity != null) {
                    if (cooling != null || heating != null) filtersBuilder.append(",");
                    filtersBuilder.append(" minCapacity: ").append(minCapacity);
                }
                filtersBuilder.append(" }");
            }

            filtersBuilder.append(" }");
            hasFilters = true;
        }

        String limitStr = limit != null ? ", limit: " + limit : "";

        String query = String.format("""
            {
              drones(%s%s) {
                id
                name
                capability {
                  cooling
                  heating
                  capacity
                  maxMoves
                  costPerMove
                  costInitial
                  costFinal
                }
              }
            }
            """, hasFilters ? filtersBuilder.toString() : "", limitStr);

        try {
            JsonNode data = graphQLClient.executeQuery(query);
            JsonNode dronesNode = data.get("drones");

            return objectMapper.convertValue(
                    dronesNode,
                    new TypeReference<List<DroneInfo>>() {}
            );
        } catch (Exception e) {
            logger.error("Failed to fetch filtered drones via GraphQL", e);
            throw new RuntimeException("Failed to fetch filtered drones", e);
        }
    }

    /**
     * Fetch multiple types of data in a single GraphQL query
     * This is the KEY BENEFIT - one query instead of multiple REST calls!
     */
    public DataBundle fetchAllData() {
        logger.info("Fetching all data via single GraphQL query");

        String query = """
            {
              drones {
                id
                name
                capability {
                  cooling
                  heating
                  capacity
                  maxMoves
                  costPerMove
                  costInitial
                  costFinal
                }
              }
              servicePoints {
                id
                name
                location {
                  lng
                  lat
                }
              }
            }
            """;

        try {
            JsonNode data = graphQLClient.executeQuery(query);

            List<DroneInfo> drones = objectMapper.convertValue(
                    data.get("drones"),
                    new TypeReference<List<DroneInfo>>() {}
            );

            List<ServicePoints> servicePoints = objectMapper.convertValue(
                    data.get("servicePoints"),
                    new TypeReference<List<ServicePoints>>() {}
            );

            return new DataBundle(drones, servicePoints);

        } catch (Exception e) {
            logger.error("Failed to fetch all data via GraphQL", e);
            throw new RuntimeException("Failed to fetch all data", e);
        }
    }

    /**
     * Data bundle class to return multiple types of data from a single query
     */
    public record DataBundle(
            List<DroneInfo> drones,
            List<ServicePoints> servicePoints
    ) {}
}
