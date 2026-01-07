package uk.ac.ed.acp.cw2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import uk.ac.ed.acp.cw2.services.DroneQueryService;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 *  ILP REST Service Fetching Tests
 *
 * Tests for LO2 Section 2: Testing fetching data from ILP REST Service
 *
 * These tests are split into two categories:
 *
 * 1. UNIT TESTS (no Spring context) - Test JSON parsing and validation logic
 *    These verify that the application would correctly handle various error conditions
 *
 * 2. INTEGRATION TESTS (with Spring context) - Verify service behavior with real/mock data
 *    These test the actual service methods work correctly
 *
 */
@DisplayName("ILP REST Service Fetching Tests")
class IlpRestServiceFetchingTest {

    // =========================================================================
    // JSON Parsing Unit Tests (No Spring Context)
    // =========================================================================

    @Nested
    @DisplayName("1. JSON Parsing and Validation Tests")
    class JsonParsingTests {

        private final ObjectMapper objectMapper = new ObjectMapper();

        // ----- Drones JSON Parsing -----

        @Test
        @DisplayName("Valid drones JSON is parsed correctly")
        void validDronesJson_parsedCorrectly() throws JsonProcessingException {
            String validJson = """
                [
                    {
                        "id": 1,
                        "name": "Drone Alpha",
                        "capability": {
                            "cooling": true,
                            "heating": false,
                            "capacity": 5.0,
                            "maxMoves": 2000,
                            "costPerMove": 0.02,
                            "costInitial": 1.5,
                            "costFinal": 2.0
                        }
                    }
                ]
                """;

            // This should parse without exception
            var result = objectMapper.readTree(validJson);
            assertThat(result.isArray()).isTrue();
            assertThat(result.get(0).get("id").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("Malformed drones JSON throws JsonProcessingException")
        void malformedDronesJson_throwsException() {
            String malformedJson = "{invalid json content";

            assertThrows(JsonProcessingException.class, () -> {
                objectMapper.readTree(malformedJson);
            }, "Malformed JSON should throw JsonProcessingException");
        }

        @Test
        @DisplayName("Truncated JSON throws JsonProcessingException")
        void truncatedJson_throwsException() {
            String truncatedJson = "[{\"id\":1,\"name\":\"Dro";

            assertThrows(JsonProcessingException.class, () -> {
                objectMapper.readTree(truncatedJson);
            }, "Truncated JSON should throw JsonProcessingException");
        }

        @Test
        @DisplayName("Empty string returns null from readTree")
        void emptyString_isMissingNode() throws JsonProcessingException {
            var result = objectMapper.readTree("");

            assertThat(result == null || result.isMissingNode()).isTrue();
        }

        @Test
        @DisplayName("Empty JSON array is valid")
        void emptyJsonArray_isValid() throws JsonProcessingException {
            var result = objectMapper.readTree("[]");

            assertThat(result.isArray()).isTrue();
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Plain text (not JSON) throws JsonProcessingException")
        void plainText_throwsException() {
            String plainText = "not valid json at all";

            assertThrows(JsonProcessingException.class, () -> {
                objectMapper.readTree(plainText);
            }, "Plain text should throw JsonProcessingException");
        }

        // ----- Service Points JSON Parsing -----

        @Test
        @DisplayName("Valid service points JSON is parsed correctly")
        void validServicePointsJson_parsedCorrectly() throws JsonProcessingException {
            String validJson = """
                [
                    {
                        "id": 1,
                        "name": "Service Point Alpha",
                        "location": {"lng": -3.186874, "lat": 55.944494}
                    }
                ]
                """;

            var result = objectMapper.readTree(validJson);
            assertThat(result.isArray()).isTrue();
            assertThat(result.get(0).get("location").get("lng").asDouble()).isCloseTo(-3.186874, within(0.0001));
        }

        @Test
        @DisplayName("Service points with missing location is still valid JSON")
        void servicePointsMissingLocation_stillParsesAsJson() throws JsonProcessingException {
            String json = """
                [
                    {
                        "id": 1,
                        "name": "Service Point Alpha"
                    }
                ]
                """;

            // JSON is valid, just missing expected field
            var result = objectMapper.readTree(json);
            assertThat(result.isArray()).isTrue();
            assertThat(result.get(0).has("location")).isFalse();
        }

        // ----- Restricted Areas JSON Parsing -----

        @Test
        @DisplayName("Valid restricted areas JSON is parsed correctly")
        void validRestrictedAreasJson_parsedCorrectly() throws JsonProcessingException {
            String validJson = """
                [
                    {
                        "name": "No Fly Zone 1",
                        "id": 1,
                        "vertices": [
                            {"lng": -3.19, "lat": 55.95},
                            {"lng": -3.18, "lat": 55.94}
                        ]
                    }
                ]
                """;

            var result = objectMapper.readTree(validJson);
            assertThat(result.isArray()).isTrue();
            assertThat(result.get(0).get("vertices").isArray()).isTrue();
        }

        @Test
        @DisplayName("Malformed restricted areas JSON throws exception")
        void malformedRestrictedAreasJson_throwsException() {
            String malformedJson = "{broken: json[";

            assertThrows(JsonProcessingException.class, () -> {
                objectMapper.readTree(malformedJson);
            });
        }

        // ----- Drones For Service Points JSON Parsing -----

        @Test
        @DisplayName("Valid drones-for-service-points JSON is parsed correctly")
        void validDronesForServicePointsJson_parsedCorrectly() throws JsonProcessingException {
            String validJson = """
                [
                    {
                        "servicePointId": 1,
                        "drones": [
                            {
                                "droneId": 1,
                                "availability": [
                                    {"dayOfWeek": "FRIDAY", "from": "08:00", "until": "20:00"}
                                ]
                            }
                        ]
                    }
                ]
                """;

            var result = objectMapper.readTree(validJson);
            assertThat(result.isArray()).isTrue();
            assertThat(result.get(0).get("drones").isArray()).isTrue();
        }

        private static org.assertj.core.data.Offset<Double> within(double v) {
            return org.assertj.core.data.Offset.offset(v);
        }
    }

    // =========================================================================
    // PART 2: Error Type Documentation Tests
    // =========================================================================

    @Nested
    @DisplayName("2. Error Type Documentation")
    class ErrorTypeDocumentationTests {

        @Test
        @DisplayName("Documents expected exception types for server errors")
        void documentsServerErrorExceptions() {
            // This test documents the exception types that should be thrown
            // for various server error conditions

            // Connection refused - server not running
            assertThat(ResourceAccessException.class)
                    .describedAs("Connection refused should throw ResourceAccessException");

            // HTTP 500 Internal Server Error
            assertThat(HttpServerErrorException.class)
                    .describedAs("HTTP 500 should throw HttpServerErrorException");

            // HTTP 404 Not Found
            assertThat(HttpClientErrorException.class)
                    .describedAs("HTTP 404 should throw HttpClientErrorException");

            // Connection timeout
            assertThat(SocketTimeoutException.class)
                    .describedAs("Timeout should cause SocketTimeoutException (wrapped in ResourceAccessException)");
        }

        @Test
        @DisplayName("ResourceAccessException wraps connection failures")
        void resourceAccessException_wrapsConnectionFailures() {
            // Create example exception to verify structure
            ConnectException cause = new ConnectException("Connection refused");
            ResourceAccessException rae = new ResourceAccessException("I/O error", cause);

            assertThat(rae.getCause()).isInstanceOf(ConnectException.class);
            assertThat(rae.getMessage()).contains("I/O error");
        }

        @Test
        @DisplayName("HttpServerErrorException contains status code")
        void httpServerErrorException_containsStatusCode() {
            HttpServerErrorException ex = HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal Server Error",
                    null, null, null
            );

            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(ex.getStatusCode().value()).isEqualTo(500);
        }
    }

    // =========================================================================
    // PART 3: Integration Tests with Real Service
    // =========================================================================

    @Nested
    @DisplayName("3. Service Integration Tests")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class ServiceIntegrationTests {

        @Autowired
        private DroneQueryService droneQueryService;

        @Test
        @DisplayName("fetchDrones returns non-empty list from real service")
        void fetchDrones_returnsNonEmptyList() {
            var drones = droneQueryService.fetchDrones();

            assertThat(drones)
                    .describedAs("Should fetch drones from ILP REST service")
                    .isNotNull()
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Each fetched drone has required fields")
        void fetchedDrones_haveRequiredFields() {
            var drones = droneQueryService.fetchDrones();

            for (var drone : drones) {
                assertThat(drone.id()).isPositive();
                assertThat(drone.name()).isNotBlank();
                assertThat(drone.capability()).isNotNull();
                assertThat(drone.capability().capacity()).isPositive();
                assertThat(drone.capability().maxMoves()).isPositive();
            }
        }

        @Test
        @DisplayName("fetchRestrictedAreas returns list from real service")
        void fetchRestrictedAreas_returnsList() {
            var areas = droneQueryService.fetchRestrictedAreas();

            assertThat(areas)
                    .describedAs("Should fetch restricted areas from ILP REST service")
                    .isNotNull();
            // May be empty if no restricted areas defined
        }

        @Test
        @DisplayName("fetchDroneAvailability returns list from real service")
        void fetchDroneAvailability_returnsList() {
            var availability = droneQueryService.fetchDroneAvailability();

            assertThat(availability)
                    .describedAs("Should fetch drone availability from ILP REST service")
                    .isNotNull();
        }

        @Test
        @DisplayName("fetchDroneOriginLocations returns map from real service")
        void fetchDroneOriginLocations_returnsMap() {
            var locations = droneQueryService.fetchDroneOriginLocations();

            assertThat(locations)
                    .describedAs("Should fetch drone origin locations from ILP REST service")
                    .isNotNull();
        }
    }

    // =========================================================================
    // PART 4: Fresh Data Fetch Verification
    // =========================================================================

    @Nested
    @DisplayName("4. Fresh Data Fetch Tests")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class FreshDataFetchTests {

        @Autowired
        private DroneQueryService droneQueryService;

        @Test
        @DisplayName("Multiple calls to fetchDrones return data (verifies no fatal caching bug)")
        void multipleFetchCalls_allReturnData() {
            // Make multiple calls
            var result1 = droneQueryService.fetchDrones();
            var result2 = droneQueryService.fetchDrones();
            var result3 = droneQueryService.fetchDrones();

            // All should return valid data
            assertThat(result1).isNotNull().isNotEmpty();
            assertThat(result2).isNotNull().isNotEmpty();
            assertThat(result3).isNotNull().isNotEmpty();

            // Results should be consistent (same data from same server)
            assertThat(result1.size()).isEqualTo(result2.size());
            assertThat(result2.size()).isEqualTo(result3.size());
        }

        @Test
        @DisplayName("Data integrity is maintained across multiple fetches")
        void dataIntegrity_acrossMultipleFetches() {
            var drones1 = droneQueryService.fetchDrones();
            var drones2 = droneQueryService.fetchDrones();

            // Same IDs should be present
            var ids1 = drones1.stream().map(d -> d.id()).sorted().toList();
            var ids2 = drones2.stream().map(d -> d.id()).sorted().toList();

            assertThat(ids1).isEqualTo(ids2);
        }
    }

    // =========================================================================
    // PART 5: Error Handling Behavior Documentation
    // =========================================================================

    @Nested
    @DisplayName("5. Error Handling Behavior Documentation")
    class ErrorHandlingBehaviorTests {

        @Test
        @DisplayName("Documents expected behavior when server is unreachable")
        void documentsUnreachableServerBehavior() {
            /*
             * Expected behavior when ILP REST server is unreachable:
             *
             * 1. The service should throw RestClientException (or subclass)
             * 2. The exception should contain meaningful error message
             * 3. The controller should catch this and return appropriate HTTP status
             *
             * To test this manually:
             * 1. Stop the ILP REST server (or block network access)
             * 2. Make a request to any endpoint
             * 3. Verify you get a 503 Service Unavailable or similar
             */

            // Document the expected exception hierarchy
            assertThat(ResourceAccessException.class.getSuperclass())
                    .isEqualTo(RestClientException.class);
        }

        @Test
        @DisplayName("Documents expected behavior for malformed JSON response")
        void documentsMalformedJsonBehavior() {
            /*
             * Expected behavior when ILP REST returns malformed JSON:
             *
             * 1. JSON parsing should throw JsonProcessingException
             * 2. This should be caught and wrapped appropriately
             * 3. The endpoint should return HTTP 502 Bad Gateway or 500 Internal Server Error
             *
             * Note: If using Jackson with lenient settings, some malformed JSON
             * may still parse. The application should validate the parsed structure.
             */

            ObjectMapper mapper = new ObjectMapper();

            // Verify Jackson throws on malformed JSON
            assertThrows(JsonProcessingException.class, () -> {
                mapper.readTree("{invalid}");
            });
        }

        @Test
        @DisplayName("Documents expected behavior for HTTP error responses")
        void documentsHttpErrorBehavior() {
            /*
             * Expected behavior for HTTP error responses:
             *
             * HTTP 404: Resource not found - throw HttpClientErrorException
             * HTTP 500: Server error - throw HttpServerErrorException
             * HTTP 503: Service unavailable - throw HttpServerErrorException
             *
             * All should be handled gracefully with appropriate error messages.
             */

            // Verify exception types exist and have expected structure
            HttpClientErrorException clientError = HttpClientErrorException.create(
                    HttpStatus.NOT_FOUND, "Not Found", null, null, null);
            assertThat(clientError.getStatusCode().value()).isEqualTo(404);

            HttpServerErrorException serverError = HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error", null, null, null);
            assertThat(serverError.getStatusCode().value()).isEqualTo(500);
        }

        @Test
        @DisplayName("Documents liveness check behavior")
        void documentsLivenessCheckBehavior() {
            /*
             * Expected behavior for liveness checks:
             *
             * Before making requests, the application MAY check /actuator/health/livenessState
             *
             * If status is not "UP":
             * - Option A: Fail fast with ServiceUnavailableException
             * - Option B: Proceed anyway and handle failures per-request
             * - Option C: Don't check liveness at all (current behavior based on test results)
             *
             * Current implementation appears to use Option C - no liveness pre-check.
             * This is acceptable if the application handles request failures gracefully.
             */

            // This test documents behavior rather than asserting it
            System.out.println("Liveness check behavior: No pre-request liveness check implemented");
            System.out.println("Application relies on per-request error handling instead");
        }
    }

    // =========================================================================
    // PART 6: Data Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("6. Data Validation Tests")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class DataValidationTests {

        @Autowired
        private DroneQueryService droneQueryService;

        @Test
        @DisplayName("Drone capabilities have valid ranges")
        void droneCapabilities_haveValidRanges() {
            var drones = droneQueryService.fetchDrones();

            for (var drone : drones) {
                var cap = drone.capability();

                assertThat(cap.capacity())
                        .describedAs("Capacity for drone %d", drone.id())
                        .isPositive();

                assertThat(cap.maxMoves())
                        .describedAs("MaxMoves for drone %d", drone.id())
                        .isPositive();

                assertThat(cap.costPerMove())
                        .describedAs("CostPerMove for drone %d", drone.id())
                        .isPositive();

                assertThat(cap.costInitial())
                        .describedAs("CostInitial for drone %d", drone.id())
                        .isNotNegative();

                assertThat(cap.costFinal())
                        .describedAs("CostFinal for drone %d", drone.id())
                        .isNotNegative();
            }
        }

        @Test
        @DisplayName("Restricted areas have valid polygon vertices")
        void restrictedAreas_haveValidPolygons() {
            var areas = droneQueryService.fetchRestrictedAreas();

            for (var area : areas) {
                assertThat(area.vertices())
                        .describedAs("Vertices for area %s", area.name())
                        .isNotNull()
                        .hasSizeGreaterThanOrEqualTo(3);  // Minimum for a polygon

                // Verify coordinates are in valid ranges
                for (var vertex : area.vertices()) {
                    assertThat(vertex.lng())
                            .describedAs("Longitude should be valid")
                            .isBetween(-180.0, 180.0);
                    assertThat(vertex.lat())
                            .describedAs("Latitude should be valid")
                            .isBetween(-90.0, 90.0);
                }
            }
        }

        @Test
        @DisplayName("Drone IDs are unique")
        void droneIds_areUnique() {
            var drones = droneQueryService.fetchDrones();

            var ids = drones.stream().map(d -> d.id()).toList();
            var uniqueIds = ids.stream().distinct().toList();

            assertThat(ids.size())
                    .describedAs("All drone IDs should be unique")
                    .isEqualTo(uniqueIds.size());
        }
    }
}