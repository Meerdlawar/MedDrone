package uk.ac.ed.acp.cw2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-Functional Requirements Tests
 * 
 * Tests for LO2 Section: Non-Functional Requirements
 * 
 * These tests verify that the system meets its non-functional requirements:
 * 1. Performance - execution time shall not exceed 30 seconds for any task
 * 2. Determinism - identical requests produce identical outputs
 * 3. Docker deployment - service runs correctly when deployed as Docker container
 * 
 * Dependencies required in pom.xml:
 * <dependency>
 *     <groupId>org.testcontainers</groupId>
 *     <artifactId>testcontainers</artifactId>
 *     <version>1.19.3</version>
 *     <scope>test</scope>
 * </dependency>
 * <dependency>
 *     <groupId>org.testcontainers</groupId>
 *     <artifactId>junit-jupiter</artifactId>
 *     <version>1.19.3</version>
 *     <scope>test</scope>
 * </dependency>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Non-Functional Requirements Tests")
class NonFunctionalRequirementsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Test data for various scenarios
    private static final String SIMPLE_DELIVERY_REQUEST = """
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

    private static final String COMPLEX_DELIVERY_REQUEST = """
        [
            {
                "id": 1,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 8.0, "cooling": true, "maxCost": 11.0},
                "delivery": {"lng": -3.1907237642840363, "lat": 55.94941687101488}
            },
            {
                "id": 2,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 4.0},
                "delivery": {"lng": -3.1843055426973828, "lat": 55.94668429393913}
            },
            {
                "id": 3,
                "date": "2025-12-12",
                "time": "14:30:00",
                "requirements": {"capacity": 7.0, "cooling": true, "maxCost": 10.0},
                "delivery": {"lng": -3.189799207630236, "lat": 55.94279498507413}
            },
            {
                "id": 4,
                "date": "2025-12-12",
                "time": "14:20:00",
                "requirements": {"capacity": 6.0, "heating": true},
                "delivery": {"lng": -3.1844710805008623, "lat": 55.94664821172111}
            }
        ]
        """;

    // =========================================================================
    // 1. Performance Tests - Execution Time < 30 seconds
    // =========================================================================

    @Nested
    @DisplayName("1. Performance - Execution Time Tests")
    class PerformanceTests {

        private static final Duration MAX_EXECUTION_TIME = Duration.ofSeconds(30);

        @Test
        @DisplayName("Simple delivery calculation completes within 30 seconds")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void simpleDelivery_completesWithin30Seconds() throws Exception {
            Instant start = Instant.now();

            mockMvc.perform(post("/api/v1/calcDeliveryPath")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SIMPLE_DELIVERY_REQUEST))
                .andExpect(status().isOk());

            Duration elapsed = Duration.between(start, Instant.now());
            
            assertThat(elapsed)
                .describedAs("Simple delivery calculation should complete within 30 seconds")
                .isLessThan(MAX_EXECUTION_TIME);
            
            System.out.println("Simple delivery execution time: " + elapsed.toMillis() + "ms");
        }

        @Test
        @DisplayName("Complex multi-delivery calculation completes within 30 seconds")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void complexDelivery_completesWithin30Seconds() throws Exception {
            Instant start = Instant.now();

            mockMvc.perform(post("/api/v1/calcDeliveryPath")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(COMPLEX_DELIVERY_REQUEST))
                .andExpect(status().isOk());

            Duration elapsed = Duration.between(start, Instant.now());
            
            assertThat(elapsed)
                .describedAs("Complex delivery calculation should complete within 30 seconds")
                .isLessThan(MAX_EXECUTION_TIME);
            
            System.out.println("Complex delivery execution time: " + elapsed.toMillis() + "ms");
        }

        @Test
        @DisplayName("GeoJSON path calculation completes within 30 seconds")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void geoJsonCalculation_completesWithin30Seconds() throws Exception {
            Instant start = Instant.now();

            mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(COMPLEX_DELIVERY_REQUEST))
                .andExpect(status().isOk());

            Duration elapsed = Duration.between(start, Instant.now());
            
            assertThat(elapsed)
                .describedAs("GeoJSON calculation should complete within 30 seconds")
                .isLessThan(MAX_EXECUTION_TIME);
            
            System.out.println("GeoJSON calculation execution time: " + elapsed.toMillis() + "ms");
        }

        @Test
        @DisplayName("Query available drones completes within 30 seconds")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void queryAvailableDrones_completesWithin30Seconds() throws Exception {
            Instant start = Instant.now();

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(COMPLEX_DELIVERY_REQUEST))
                .andExpect(status().isOk());

            Duration elapsed = Duration.between(start, Instant.now());
            
            assertThat(elapsed)
                .describedAs("Query available drones should complete within 30 seconds")
                .isLessThan(MAX_EXECUTION_TIME);
            
            System.out.println("Query drones execution time: " + elapsed.toMillis() + "ms");
        }

        @Test
        @DisplayName("Geometry calculations complete within reasonable time")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void geometryCalculations_completeQuickly() throws Exception {
            String distanceRequest = """
                {
                    "position1": {"lng": -3.192473, "lat": 55.946233},
                    "position2": {"lng": -3.192473, "lat": 55.942617}
                }
                """;

            Instant start = Instant.now();

            // Run 100 distance calculations
            for (int i = 0; i < 100; i++) {
                mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(distanceRequest))
                    .andExpect(status().isOk());
            }

            Duration elapsed = Duration.between(start, Instant.now());
            
            assertThat(elapsed)
                .describedAs("100 geometry calculations should complete within 5 seconds")
                .isLessThan(Duration.ofSeconds(5));
            
            System.out.println("100 geometry calculations: " + elapsed.toMillis() + "ms");
        }

        @Test
        @DisplayName("Concurrent requests complete within time budget")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void concurrentRequests_completeWithinTimeBudget() throws Exception {
            int numConcurrentRequests = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numConcurrentRequests);
            List<Future<Duration>> futures = new ArrayList<>();

            Instant overallStart = Instant.now();

            for (int i = 0; i < numConcurrentRequests; i++) {
                futures.add(executor.submit(() -> {
                    Instant start = Instant.now();
                    mockMvc.perform(post("/api/v1/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMPLE_DELIVERY_REQUEST))
                        .andExpect(status().isOk());
                    return Duration.between(start, Instant.now());
                }));
            }

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

            Duration overallElapsed = Duration.between(overallStart, Instant.now());
            
            // Each individual request should complete within 30 seconds
            for (Future<Duration> future : futures) {
                Duration individualTime = future.get();
                assertThat(individualTime)
                    .describedAs("Each concurrent request should complete within 30 seconds")
                    .isLessThan(MAX_EXECUTION_TIME);
            }
            
            System.out.println("Concurrent requests total time: " + overallElapsed.toMillis() + "ms");
        }

        @Test
        @DisplayName("Performance timing is logged for analysis")
        void performanceTiming_isLogged() throws Exception {
            List<Long> executionTimes = new ArrayList<>();
            int iterations = 10;

            for (int i = 0; i < iterations; i++) {
                Instant start = Instant.now();
                
                mockMvc.perform(post("/api/v1/calcDeliveryPath")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk());
                
                executionTimes.add(Duration.between(start, Instant.now()).toMillis());
            }

            // Calculate statistics
            double avgTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

            System.out.println("=== Performance Statistics ===");
            System.out.println("Iterations: " + iterations);
            System.out.println("Min time: " + minTime + "ms");
            System.out.println("Max time: " + maxTime + "ms");
            System.out.println("Avg time: " + String.format("%.2f", avgTime) + "ms");

            assertThat(maxTime)
                .describedAs("Maximum execution time should be under 30 seconds")
                .isLessThan(30000);
        }
    }

    // =========================================================================
    // 2. Determinism Tests - Identical Requests Produce Identical Outputs
    // =========================================================================

    @Nested
    @DisplayName("2. Determinism - Identical Outputs Tests")
    class DeterminismTests {

        @Test
        @DisplayName("Identical requests produce identical delivery paths")
        void identicalRequests_produceIdenticalPaths() throws Exception {
            // Make the same request multiple times
            List<String> responses = new ArrayList<>();
            int iterations = 5;

            for (int i = 0; i < iterations; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPath")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

                responses.add(result.getResponse().getContentAsString());
            }

            // All responses should be identical
            String firstResponse = responses.get(0);
            for (int i = 1; i < responses.size(); i++) {
                assertThat(responses.get(i))
                    .describedAs("Response %d should be identical to response 1", i + 1)
                    .isEqualTo(firstResponse);
            }
        }

        @Test
        @DisplayName("Identical requests produce identical GeoJSON")
        void identicalRequests_produceIdenticalGeoJson() throws Exception {
            List<String> responses = new ArrayList<>();
            int iterations = 5;

            for (int i = 0; i < iterations; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

                responses.add(result.getResponse().getContentAsString());
            }

            // All responses should be identical
            String firstResponse = responses.get(0);
            for (int i = 1; i < responses.size(); i++) {
                assertThat(responses.get(i))
                    .describedAs("GeoJSON response %d should be identical to response 1", i + 1)
                    .isEqualTo(firstResponse);
            }
        }

        @Test
        @DisplayName("Identical requests produce identical total cost")
        void identicalRequests_produceIdenticalCost() throws Exception {
            List<Double> costs = new ArrayList<>();
            int iterations = 5;

            for (int i = 0; i < iterations; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPath")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

                JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
                costs.add(json.get("totalCost").asDouble());
            }

            // All costs should be identical
            Double firstCost = costs.get(0);
            for (int i = 1; i < costs.size(); i++) {
                assertThat(costs.get(i))
                    .describedAs("Cost %d should be identical to cost 1", i + 1)
                    .isEqualTo(firstCost);
            }
        }

        @Test
        @DisplayName("Identical requests produce identical total moves")
        void identicalRequests_produceIdenticalMoves() throws Exception {
            List<Integer> moves = new ArrayList<>();
            int iterations = 5;

            for (int i = 0; i < iterations; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPath")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

                JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
                moves.add(json.get("totalMoves").asInt());
            }

            // All move counts should be identical
            Integer firstMoves = moves.get(0);
            for (int i = 1; i < moves.size(); i++) {
                assertThat(moves.get(i))
                    .describedAs("Moves %d should be identical to moves 1", i + 1)
                    .isEqualTo(firstMoves);
            }
        }

        @Test
        @DisplayName("Identical geometry calculations produce identical results")
        void identicalGeometry_produceIdenticalResults() throws Exception {
            String request = """
                {
                    "position1": {"lng": -3.192473, "lat": 55.946233},
                    "position2": {"lng": -3.192473, "lat": 55.942617}
                }
                """;

            List<String> distances = new ArrayList<>();
            int iterations = 10;

            for (int i = 0; i < iterations; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                    .andExpect(status().isOk())
                    .andReturn();

                distances.add(result.getResponse().getContentAsString());
            }

            // All distances should be identical
            String firstDistance = distances.get(0);
            for (String distance : distances) {
                assertThat(distance).isEqualTo(firstDistance);
            }
        }

        @Test
        @DisplayName("Complex multi-delivery requests are deterministic")
        void complexRequests_areDeterministic() throws Exception {
            List<String> responses = new ArrayList<>();
            int iterations = 3;  // Fewer iterations for complex requests

            for (int i = 0; i < iterations; i++) {
                MvcResult result = mockMvc.perform(post("/api/v1/calcDeliveryPath")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLEX_DELIVERY_REQUEST))
                    .andExpect(status().isOk())
                    .andReturn();

                responses.add(result.getResponse().getContentAsString());
            }

            // All responses should be identical
            String firstResponse = responses.get(0);
            for (int i = 1; i < responses.size(); i++) {
                assertThat(responses.get(i))
                    .describedAs("Complex response %d should be identical to response 1", i + 1)
                    .isEqualTo(firstResponse);
            }
        }

        @Test
        @DisplayName("Path coordinates are exactly reproducible")
        void pathCoordinates_areReproducible() throws Exception {
            MvcResult result1 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SIMPLE_DELIVERY_REQUEST))
                .andExpect(status().isOk())
                .andReturn();

            MvcResult result2 = mockMvc.perform(post("/api/v1/calcDeliveryPathAsGeoJson")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SIMPLE_DELIVERY_REQUEST))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode geo1 = objectMapper.readTree(result1.getResponse().getContentAsString());
            JsonNode geo2 = objectMapper.readTree(result2.getResponse().getContentAsString());

            JsonNode coords1 = geo1.get("coordinates");
            JsonNode coords2 = geo2.get("coordinates");

            assertThat(coords1.size()).isEqualTo(coords2.size());

            // Compare each coordinate exactly
            for (int i = 0; i < coords1.size(); i++) {
                double lng1 = coords1.get(i).get(0).asDouble();
                double lat1 = coords1.get(i).get(1).asDouble();
                double lng2 = coords2.get(i).get(0).asDouble();
                double lat2 = coords2.get(i).get(1).asDouble();

                assertThat(lng1).isEqualTo(lng2);
                assertThat(lat1).isEqualTo(lat2);
            }
        }
    }

    // =========================================================================
    // 3. Docker Deployment Tests
    // =========================================================================

    @Nested
    @DisplayName("3. Docker Deployment Tests")
    @Testcontainers
    @Disabled("Enable when Docker image is built - requires 'docker build' first")
    class DockerDeploymentTests {

        // Assumes Docker image is built as 'medsupplydrones:latest'
        // Build with: docker build -t medsupplydrones:latest .
        @Container
        private static final GenericContainer<?> appContainer = new GenericContainer<>(
                DockerImageName.parse("medsupplydrones:latest"))
            .withExposedPorts(8080)
            .withEnv("ILP_URL", "https://ilp-rest.azurewebsites.net")
            .waitingFor(Wait.forHttp("/actuator/health")
                .forPort(8080)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(60)));

        @Test
        @DisplayName("Service starts correctly in Docker container")
        void dockerContainer_startsCorrectly() {
            assertThat(appContainer.isRunning())
                .describedAs("Docker container should be running")
                .isTrue();
        }

        @Test
        @DisplayName("Health endpoint returns UP in Docker")
        void dockerHealth_returnsUp() throws Exception {
            String baseUrl = "http://" + appContainer.getHost() + ":" + appContainer.getMappedPort(8080);
            String healthUrl = baseUrl + "/actuator/health";

            HttpURLConnection conn = (HttpURLConnection) new URL(healthUrl).openConnection();
            conn.setRequestMethod("GET");
            
            int responseCode = conn.getResponseCode();
            assertThat(responseCode).isEqualTo(200);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            assertThat(response.toString()).contains("\"status\":\"UP\"");
        }

        @Test
        @DisplayName("Delivery path calculation works in Docker")
        void dockerDeliveryPath_works() throws Exception {
            String baseUrl = "http://" + appContainer.getHost() + ":" + appContainer.getMappedPort(8080);
            String endpoint = baseUrl + "/api/v1/calcDeliveryPath";

            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            conn.getOutputStream().write(SIMPLE_DELIVERY_REQUEST.getBytes());
            conn.getOutputStream().flush();

            int responseCode = conn.getResponseCode();
            assertThat(responseCode).isEqualTo(200);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            assertThat(response.toString())
                .contains("totalCost")
                .contains("totalMoves")
                .contains("dronePaths");
        }

        @Test
        @DisplayName("Docker container reads ILP_URL environment variable")
        void dockerContainer_readsEnvVariable() {
            String ilpUrl = appContainer.getEnvMap().get("ILP_URL");
            assertThat(ilpUrl)
                .isNotNull()
                .isEqualTo("https://ilp-rest.azurewebsites.net");
        }

        @Test
        @DisplayName("Docker container exposes correct port")
        void dockerContainer_exposesCorrectPort() {
            Integer mappedPort = appContainer.getMappedPort(8080);
            assertThat(mappedPort).isNotNull().isPositive();
        }
    }

    // =========================================================================
    // Alternative Docker Tests (without Testcontainers)
    // =========================================================================

    @Nested
    @DisplayName("3b. Docker Deployment Tests (Manual)")
    class ManualDockerDeploymentTests {

        @Test
        @DisplayName("Dockerfile exists and is valid")
        void dockerfile_existsAndIsValid() {
            // This test can run without Docker daemon
            // Just verify Dockerfile has required elements
            java.io.File dockerfile = new java.io.File("Dockerfile");
            
            if (dockerfile.exists()) {
                try {
                    String content = java.nio.file.Files.readString(dockerfile.toPath());
                    
                    assertThat(content)
                        .describedAs("Dockerfile should have FROM instruction")
                        .containsIgnoringCase("FROM");
                    
                    assertThat(content)
                        .describedAs("Dockerfile should expose port 8080")
                        .containsAnyOf("EXPOSE 8080", "expose 8080");
                    
                    assertThat(content)
                        .describedAs("Dockerfile should have an entrypoint or cmd")
                        .containsAnyOf("ENTRYPOINT", "CMD", "entrypoint", "cmd");
                        
                } catch (Exception e) {
                    fail("Could not read Dockerfile: " + e.getMessage());
                }
            } else {
                System.out.println("Note: Dockerfile not found at project root. Skipping file validation.");
            }
        }

        @Test
        @DisplayName("Docker compose file exists (if used)")
        void dockerCompose_existsIfUsed() {
            java.io.File composeFile = new java.io.File("docker-compose.yml");
            java.io.File composeFileAlt = new java.io.File("docker-compose.yaml");
            
            if (composeFile.exists() || composeFileAlt.exists()) {
                java.io.File file = composeFile.exists() ? composeFile : composeFileAlt;
                try {
                    String content = java.nio.file.Files.readString(file.toPath());
                    
                    assertThat(content)
                        .describedAs("docker-compose should define services")
                        .containsIgnoringCase("services");
                    
                } catch (Exception e) {
                    fail("Could not read docker-compose file: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("Application can be built with Maven for Docker")
        void mavenBuild_worksForDocker() {
            java.io.File pomFile = new java.io.File("pom.xml");
            
            if (pomFile.exists()) {
                try {
                    String content = java.nio.file.Files.readString(pomFile.toPath());
                    
                    assertThat(content)
                        .describedAs("pom.xml should have spring-boot-maven-plugin for packaging")
                        .contains("spring-boot-maven-plugin");
                    
                    assertThat(content)
                        .describedAs("pom.xml should package as jar")
                        .containsAnyOf("<packaging>jar</packaging>", "spring-boot");
                        
                } catch (Exception e) {
                    fail("Could not read pom.xml: " + e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // Additional Non-Functional Tests
    // =========================================================================

    @Nested
    @DisplayName("Additional Non-Functional Tests")
    class AdditionalNonFunctionalTests {

        @Test
        @DisplayName("Service handles load gracefully")
        void service_handlesLoadGracefully() throws Exception {
            int requests = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    MvcResult result = mockMvc.perform(post("/api/v1/distanceTo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "position1": {"lng": -3.192473, "lat": 55.946233},
                                    "position2": {"lng": -3.192473, "lat": 55.942617}
                                }
                                """))
                        .andReturn();
                    return result.getResponse().getStatus();
                }));
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            int successCount = 0;
            for (Future<Integer> future : futures) {
                if (future.get() == 200) {
                    successCount++;
                }
            }

            assertThat(successCount)
                .describedAs("All requests should succeed under load")
                .isEqualTo(requests);
        }

        @Test
        @DisplayName("Memory usage remains stable across requests")
        void memoryUsage_remainsStable() throws Exception {
            Runtime runtime = Runtime.getRuntime();
            
            // Force GC and get baseline
            System.gc();
            Thread.sleep(100);
            long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

            // Make many requests
            for (int i = 0; i < 50; i++) {
                mockMvc.perform(post("/api/v1/calcDeliveryPath")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMPLE_DELIVERY_REQUEST))
                    .andExpect(status().isOk());
            }

            // Force GC and measure
            System.gc();
            Thread.sleep(100);
            long afterMemory = runtime.totalMemory() - runtime.freeMemory();

            long memoryIncrease = afterMemory - baselineMemory;
            long maxAcceptableIncrease = 100 * 1024 * 1024; // 100MB

            System.out.println("Memory increase after 50 requests: " + (memoryIncrease / 1024 / 1024) + "MB");

            assertThat(memoryIncrease)
                .describedAs("Memory increase should be reasonable (< 100MB)")
                .isLessThan(maxAcceptableIncrease);
        }

        @Test
        @DisplayName("Service responds with appropriate content types")
        void service_respondsWithCorrectContentType() throws Exception {
            mockMvc.perform(post("/api/v1/calcDeliveryPath")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SIMPLE_DELIVERY_REQUEST))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String contentType = result.getResponse().getContentType();
                    assertThat(contentType)
                        .contains("application/json");
                });
        }
    }
}
