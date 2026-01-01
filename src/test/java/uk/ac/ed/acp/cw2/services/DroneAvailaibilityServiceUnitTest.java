package uk.ac.ed.acp.cw2.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.ac.ed.acp.cw2.dto.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DroneAvailabilityService maxCost Tests")
class DroneAvailabilityServiceMaxCostTest {

    @Mock
    private DroneQueryService droneQueryService;

    private DroneAvailabilityService availabilityService;

    @BeforeEach
    void setUp() throws Exception {
        // Use reflection to create the service with mocked dependency
        var constructor = DroneAvailabilityService.class.getDeclaredConstructor(DroneQueryService.class);
        constructor.setAccessible(true);
        availabilityService = constructor.newInstance(droneQueryService);
    }

    private List<DroneInfo> getTestDrones() {
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

    private List<DronesForServicePoints> getTestAvailability() {
        return List.of(
                new DronesForServicePoints(1, List.of(
                        new ListDrones(1, List.of(
                                new DroneAvailability("FRIDAY", LocalTime.of(12, 0), LocalTime.of(23, 59, 59))
                        )),
                        new ListDrones(2, List.of(
                                new DroneAvailability("FRIDAY", LocalTime.of(0, 0), LocalTime.of(23, 59, 59))
                        )),
                        new ListDrones(3, List.of(
                                new DroneAvailability("FRIDAY", LocalTime.of(12, 0), LocalTime.of(23, 59, 59))
                        )),
                        new ListDrones(5, List.of(
                                new DroneAvailability("FRIDAY", LocalTime.of(0, 0), LocalTime.of(23, 59, 59))
                        )),
                        new ListDrones(7, List.of(
                                new DroneAvailability("FRIDAY", LocalTime.of(0, 0), LocalTime.of(23, 59, 59))
                        ))
                ))
        );
    }

    @Nested
    @DisplayName("maxCost Filtering")
    class MaxCostFilteringTests {

        @Test
        @DisplayName("maxCost 0.01 with costPerMove 0.01 returns empty (strict less than)")
        void maxCost_equalToCostPerMove_returnsEmpty() {
            // Drone 1 has costPerMove=0.01, maxCost requirement is 0.01
            // Since we use < (not <=), this should return empty

            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{1, 2, 3, 5, 7}); // All pass capacity check
            when(droneQueryService.fetchDrones()).thenReturn(getTestDrones());
            // Note: fetchDroneAvailability() won't be called because maxCost filter returns empty

            MedDispatchRec dispatch = new MedDispatchRec(
                    1,
                    LocalDate.of(2025, 12, 12), // Friday
                    LocalTime.of(14, 30),
                    new DispatchRequirements(1.0, false, false, 0.01), // maxCost=0.01
                    new LngLat(-3.188374, 55.944494)
            );

            int[] result = availabilityService.queryAvailableDrones(List.of(dispatch));

            assertEquals(0, result.length, "Should return empty - no drone has costPerMove < 0.01");
        }

        @Test
        @DisplayName("maxCost 0.02 returns drones with costPerMove < 0.02")
        void maxCost_greaterThanSome_returnsMatching() {
            // Drones with costPerMove < 0.02: Drone 1 (0.01), Drone 7 (0.015)

            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{1, 2, 3, 5, 7});
            when(droneQueryService.fetchDrones()).thenReturn(getTestDrones());
            when(droneQueryService.fetchDroneAvailability()).thenReturn(getTestAvailability());

            MedDispatchRec dispatch = new MedDispatchRec(
                    1,
                    LocalDate.of(2025, 12, 12), // Friday
                    LocalTime.of(14, 30),
                    new DispatchRequirements(1.0, false, false, 0.02), // maxCost=0.02
                    new LngLat(-3.188374, 55.944494)
            );

            int[] result = availabilityService.queryAvailableDrones(List.of(dispatch));

            assertArrayEquals(new int[]{1, 7}, result,
                    "Should return drones with costPerMove < 0.02");
        }

        @Test
        @DisplayName("maxCost 0.05 returns drones with costPerMove < 0.05")
        void maxCost_greaterThanMany_returnsMatching() {
            // Drones with costPerMove < 0.05: 1 (0.01), 2 (0.03), 5 (0.04), 7 (0.015)
            // Drone 3 has costPerMove=0.05, should be excluded (not < 0.05)

            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{1, 2, 3, 5, 7});
            when(droneQueryService.fetchDrones()).thenReturn(getTestDrones());
            when(droneQueryService.fetchDroneAvailability()).thenReturn(getTestAvailability());

            MedDispatchRec dispatch = new MedDispatchRec(
                    1,
                    LocalDate.of(2025, 12, 12), // Friday
                    LocalTime.of(14, 30),
                    new DispatchRequirements(1.0, false, false, 0.05), // maxCost=0.05
                    new LngLat(-3.188374, 55.944494)
            );

            int[] result = availabilityService.queryAvailableDrones(List.of(dispatch));

            assertArrayEquals(new int[]{1, 2, 5, 7}, result,
                    "Should return drones with costPerMove < 0.05");
        }

        @Test
        @DisplayName("No maxCost specified returns all drones matching other criteria")
        void noMaxCost_returnsAllMatching() {
            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{1, 2, 3, 5, 7});
            when(droneQueryService.fetchDrones()).thenReturn(getTestDrones());
            when(droneQueryService.fetchDroneAvailability()).thenReturn(getTestAvailability());

            MedDispatchRec dispatch = new MedDispatchRec(
                    1,
                    LocalDate.of(2025, 12, 12), // Friday
                    LocalTime.of(14, 30),
                    new DispatchRequirements(1.0, false, false, null), // No maxCost
                    new LngLat(-3.188374, 55.944494)
            );

            int[] result = availabilityService.queryAvailableDrones(List.of(dispatch));

            assertEquals(5, result.length, "Should return all drones when no maxCost specified");
        }

        @Test
        @DisplayName("Multiple dispatches uses strictest (minimum) maxCost")
        void multipleDispatches_usesMinimumMaxCost() {
            when(droneQueryService.filterDroneAttributes(anyList()))
                    .thenReturn(new int[]{1, 2, 3, 5, 7});
            when(droneQueryService.fetchDrones()).thenReturn(getTestDrones());
            when(droneQueryService.fetchDroneAvailability()).thenReturn(getTestAvailability());

            List<MedDispatchRec> dispatches = List.of(
                    new MedDispatchRec(1, LocalDate.of(2025, 12, 12), LocalTime.of(14, 30),
                            new DispatchRequirements(0.5, false, false, 0.05), // maxCost=0.05
                            new LngLat(-3.188374, 55.944494)),
                    new MedDispatchRec(2, LocalDate.of(2025, 12, 12), LocalTime.of(14, 30),
                            new DispatchRequirements(0.5, false, false, 0.02), // maxCost=0.02 (strictest)
                            new LngLat(-3.188374, 55.944494))
            );

            int[] result = availabilityService.queryAvailableDrones(dispatches);

            // Should use 0.02 as the limit (strictest)
            assertArrayEquals(new int[]{1, 7}, result,
                    "Should use minimum maxCost (0.02) across all dispatches");
        }
    }
}