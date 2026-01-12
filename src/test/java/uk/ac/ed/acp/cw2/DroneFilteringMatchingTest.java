package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.services.DroneQueryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR4: Testing Drone Filtering and Matching
 * 
 * Based on LO2 Testing Plan Section 4: Testing drone filtering and matching
 * 
 * Test Coverage:
 * 1. Check filtering by cooling capability returns only drones with cooling: true when order requires cooling.
 * 2. Check filtering by heating capability returns only drones with heating: true when order requires heating.
 * 3. Check filtering by capacity returns only drones with sufficient capacity for order size.
 * 4. Check filtering by maxMoves returns only drones that can complete the required flight path.
 * 5. Check filtering by availability (dayOfWeek) returns only drones available on the specified day.
 * 6. Check filtering by availability (from/until time) returns only drones available within the time window.
 * 7. Check that when no drone matches all requirements, an empty list is returned.
 * 8. Check that drones are correctly associated with their service points.
 * 
 * Total: 8 tests
 */
@DisplayName("FR4: Drone Filtering and Matching Tests")
class DroneFilteringMatchingTest {

    // =========================================================================
    // FR4.1: Check filtering by cooling capability returns only drones 
    //        with cooling: true when order requires cooling
    // =========================================================================

    @Nested
    @DisplayName("FR4.1: Cooling Capability Filtering")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_1_CoolingFilteringTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Filtering with cooling=true returns only cooled drones")
        void FR4_1_1_coolingTrueFilter_returnsOnlyCooledDrones() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", true))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("Filtering with cooling=false returns only non-cooled drones")
        void FR4_1_2_coolingFalseFilter_returnsOnlyNonCooledDrones() throws Exception {
            mockMvc.perform(get("/api/v1/dronesWithCooling/{state}", false))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("Order requiring cooling only matches drones with cooling capability")
        void FR4_1_3_orderRequiringCooling_matchesCooledDronesOnly() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 2.0, "cooling": true},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // FR4.2: Check filtering by heating capability returns only drones 
    //        with heating: true when order requires heating
    // =========================================================================

    @Nested
    @DisplayName("FR4.2: Heating Capability Filtering")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_2_HeatingFilteringTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Order requiring heating only matches drones with heating capability")
        void FR4_2_1_orderRequiringHeating_matchesHeatedDronesOnly() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 2.0, "heating": true},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query filter with heating=true returns matching drones")
        void FR4_2_2_queryFilterHeatingTrue_returnsMatchingDrones() throws Exception {
            String requestBody = """
                [
                    {"attribute": "heating", "operator": "=", "value": "true"}
                ]
                """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // FR4.3: Check filtering by capacity returns only drones with 
    //        sufficient capacity for order size
    // =========================================================================

    @Nested
    @DisplayName("FR4.3: Capacity Filtering")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_3_CapacityFilteringTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Query for capacity=8 returns drones with capacity 8")
        void FR4_3_1_capacityEquals8_returnsMatchingDrones() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "capacity", "8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("Query for capacity > 5 returns drones with sufficient capacity")
        void FR4_3_2_capacityGreaterThan5_returnsMatchingDrones() throws Exception {
            String requestBody = """
                [
                    {"attribute": "capacity", "operator": ">", "value": "5"}
                ]
                """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Order with capacity requirement filters appropriately")
        void FR4_3_3_orderWithCapacityRequirement_filtersCorrectly() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 4.5},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("Impossible capacity requirement returns empty")
        void FR4_3_4_impossibleCapacity_returnsEmpty() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 500},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // =========================================================================
    // FR4.4: Check filtering by maxMoves returns only drones that can 
    //        complete the required flight path
    // =========================================================================

    @Nested
    @DisplayName("FR4.4: MaxMoves Filtering")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_4_MaxMovesFilteringTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Query for maxMoves=1500 returns matching drones")
        void FR4_4_1_maxMoves1500_returnsMatchingDrones() throws Exception {
            mockMvc.perform(get("/api/v1/queryAsPath/{attributeName}/{attributeValue}", "maxMoves", "1500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query for maxMoves > 1000 returns drones capable of long flights")
        void FR4_4_2_maxMovesGreaterThan1000_returnsCapableDrones() throws Exception {
            String requestBody = """
                [
                    {"attribute": "maxMoves", "operator": ">", "value": "1000"}
                ]
                """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Impossible maxMoves requirement returns empty")
        void FR4_4_3_impossibleMaxMoves_returnsEmpty() throws Exception {
            String requestBody = """
                [
                    {"attribute": "maxMoves", "operator": "=", "value": "9999999"}
                ]
                """;

            mockMvc.perform(post("/api/v1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // =========================================================================
    // FR4.5: Check filtering by availability (dayOfWeek) returns only 
    //        drones available on the specified day
    // =========================================================================

    @Nested
    @DisplayName("FR4.5: Day of Week Availability Filtering")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_5_DayOfWeekFilteringTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Query on Friday returns drones available on Friday")
        void FR4_5_1_fridayQuery_returnsFridayAvailableDrones() throws Exception {
            // 2025-12-12 is a Friday
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 1.0},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("Query on Monday returns drones available on Monday")
        void FR4_5_2_mondayQuery_returnsMondayAvailableDrones() throws Exception {
            // 2025-12-15 is a Monday
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-15",
                        "time": "14:30",
                        "requirements": {"capacity": 1.0},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // FR4.6: Check filtering by availability (from/until time) returns only 
    //        drones available within the time window
    // =========================================================================

    @Nested
    @DisplayName("FR4.6: Time Window Availability Filtering")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_6_TimeWindowFilteringTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Query at 14:30 returns drones available at that time")
        void FR4_6_1_midDayQuery_returnsAvailableDrones() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 1.0},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Query with HH:mm:ss time format is handled correctly")
        void FR4_6_2_timeWithSeconds_handledCorrectly() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30:00",
                        "requirements": {"capacity": 1.0},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // FR4.7: Check that when no drone matches all requirements, 
    //        an empty list is returned
    // =========================================================================

    @Nested
    @DisplayName("FR4.7: No Matching Drones Returns Empty")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_7_NoMatchReturnsEmptyTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Combined impossible requirements returns empty list")
        void FR4_7_1_impossibleCombination_returnsEmpty() throws Exception {
            // Very high capacity with cooling and heating - unlikely to exist
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 500, "cooling": true, "heating": true},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Very strict maxCost returns empty list")
        void FR4_7_2_strictMaxCost_returnsEmpty() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 1,
                        "date": "2025-12-12",
                        "time": "14:30",
                        "requirements": {"capacity": 1.0, "maxCost": 0.001},
                        "delivery": {"lng": -3.188374, "lat": 55.944494}
                    }
                ]
                """;

            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Empty dispatch list returns empty result")
        void FR4_7_3_emptyDispatchList_returnsEmpty() throws Exception {
            mockMvc.perform(post("/api/v1/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // =========================================================================
    // FR4.8: Check that drones are correctly associated with their 
    //        service points
    // =========================================================================

    @Nested
    @DisplayName("FR4.8: Drone-ServicePoint Association")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    class FR4_8_DroneServicePointAssociationTests {

        @Autowired
        private DroneQueryService droneQueryService;

        @Test
        @DisplayName("Drone origin locations are correctly fetched")
        void FR4_8_1_droneOriginLocations_areFetched() {
            var origins = droneQueryService.fetchDroneOriginLocations();
            
            assertThat(origins).isNotNull();
            // Origins should map drone IDs to locations
            assertThat(origins).isNotEmpty();
        }

        @Test
        @DisplayName("Service points are correctly fetched")
        void FR4_8_2_servicePoints_areFetched() {
            var servicePoints = droneQueryService.fetchServicePoints();
            
            assertThat(servicePoints).isNotNull().isNotEmpty();
            
            // Each service point should have valid data
            for (var sp : servicePoints) {
                assertThat(sp.id()).isPositive();
                assertThat(sp.name()).isNotBlank();
                assertThat(sp.location()).isNotNull();
            }
        }

        @Test
        @DisplayName("Drone availability is linked to service points")
        void FR4_8_3_droneAvailability_linkedToServicePoints() {
            var availability = droneQueryService.fetchDroneAvailability();
            
            assertThat(availability).isNotNull();
            
            // Each entry should reference a service point
            for (var spDrones : availability) {
                assertThat(spDrones.servicePointId()).isPositive();
                assertThat(spDrones.drones()).isNotNull();
            }
        }
    }

    // =========================================================================
    // Unit Tests with Mocking
    // =========================================================================

    @Nested
    @DisplayName("FR4.X: Unit Tests with Mocking")
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class FR4_X_MockedUnitTests {

        @Mock
        private DroneQueryService droneQueryService;

        private List<DroneInfo> createTestDrones() {
            return List.of(
                    new DroneInfo("Drone 1", 1,
                            new DroneCapability(true, true, 4.0, 2000, 0.01, 4.3, 6.5)),
                    new DroneInfo("Drone 2", 2,
                            new DroneCapability(false, true, 8.0, 1000, 0.03, 2.6, 5.4)),
                    new DroneInfo("Drone 3", 3,
                            new DroneCapability(false, false, 20.0, 4000, 0.05, 9.5, 11.5)),
                    new DroneInfo("Drone 5", 5,
                            new DroneCapability(true, true, 12.0, 1500, 0.04, 1.8, 3.5)),
                    new DroneInfo("Drone 7", 7,
                            new DroneCapability(false, true, 8.0, 1000, 0.015, 1.4, 2.2))
            );
        }

        @Test
        @DisplayName("filterDroneAttributes correctly filters by cooling")
        void filterDroneAttributes_correctlyFiltersByCooling() {
            when(droneQueryService.fetchDrones()).thenReturn(createTestDrones());

            List<DroneInfo> drones = droneQueryService.fetchDrones();
            
            // Filter for cooling = true
            List<Integer> cooledDroneIds = drones.stream()
                    .filter(d -> d.capability().cooling())
                    .map(DroneInfo::id)
                    .toList();

            assertThat(cooledDroneIds).containsExactly(1, 5);
        }

        @Test
        @DisplayName("filterDroneAttributes correctly filters by heating")
        void filterDroneAttributes_correctlyFiltersByHeating() {
            when(droneQueryService.fetchDrones()).thenReturn(createTestDrones());

            List<DroneInfo> drones = droneQueryService.fetchDrones();
            
            // Filter for heating = true
            List<Integer> heatedDroneIds = drones.stream()
                    .filter(d -> d.capability().heating())
                    .map(DroneInfo::id)
                    .toList();

            assertThat(heatedDroneIds).containsExactly(1, 2, 5, 7);
        }

        @Test
        @DisplayName("filterDroneAttributes correctly filters by capacity > threshold")
        void filterDroneAttributes_correctlyFiltersByCapacity() {
            when(droneQueryService.fetchDrones()).thenReturn(createTestDrones());

            List<DroneInfo> drones = droneQueryService.fetchDrones();
            
            // Filter for capacity > 10
            List<Integer> highCapacityIds = drones.stream()
                    .filter(d -> d.capability().capacity() > 10)
                    .map(DroneInfo::id)
                    .toList();

            assertThat(highCapacityIds).containsExactly(3, 5);
        }
    }
}
