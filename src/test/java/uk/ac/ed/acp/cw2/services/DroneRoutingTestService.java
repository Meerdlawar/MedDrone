package uk.ac.ed.acp.cw2.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroneRoutingServiceTest {

    @Mock
    private DroneAvailabilityService availabilityService;

    @Mock
    private DroneQueryService droneQueryService;

    private DroneRoutingService droneRoutingService;

    @BeforeEach
    void setUp() {
        droneRoutingService = new DroneRoutingService(availabilityService, droneQueryService);
    }

    @Test
    void calcDeliveryPlan_returnsEmptyPlanWhenNoDronesAvailable() {
        // Arrange
        when(availabilityService.queryAvailableDrones(anyList()))
                .thenReturn(new int[0]); // no drones

        // minimal valid MedDispatchRec: id + requirements.capacity
        DispatchRequirements reqs = new DispatchRequirements(0.5, false, false, null);
        LngLat delivery = new LngLat(-3.186, 55.944);
        MedDispatchRec order = new MedDispatchRec(1, null, null, reqs, delivery);

        // Act
        DeliveryPlan plan = droneRoutingService.calcDeliveryPlan(List.of(order));

        // Assert
        assertNotNull(plan);
        assertEquals(0.0, plan.totalCost(), 1e-9);
        assertEquals(0, plan.totalMoves());
        assertTrue(plan.dronePaths().isEmpty());
    }

    @Test
    void calcDeliveryPlan_singleDrone_twoDeliveries_sameLocation_oneString() {
        // Arrange
        int droneId = 1;

        // One available drone
        when(availabilityService.queryAvailableDrones(anyList()))
                .thenReturn(new int[]{droneId});

        // Service point / origin for this drone
        LngLat servicePoint = new LngLat(-3.186, 55.944);
        when(droneQueryService.fetchDroneOriginLocations())
                .thenReturn(Map.of(droneId, servicePoint));

        // Drone capability (adjust order if your record differs)
        DroneCapability capability = new DroneCapability(
                false,      // cooling
                false,      // heating
                10.0,       // capacity
                1_000,      // maxMoves
                0.01,       // costPerMove
                1.0,        // costInitial
                1.0         // costFinal
        );

        // DroneInfo(name, id, capability)
        DroneInfo droneInfo = new DroneInfo("TestDrone", droneId, capability);
        when(droneQueryService.fetchDrones())
                .thenReturn(List.of(droneInfo));

        // Two orders at exactly the SAME coordinate as the service point.
        // This makes the A* path trivial (0 moves).
        DispatchRequirements reqs1 = new DispatchRequirements(0.5, false, false, null);
        DispatchRequirements reqs2 = new DispatchRequirements(0.7, false, false, null);

        MedDispatchRec order1 = new MedDispatchRec(1, null, null, reqs1, servicePoint);
        MedDispatchRec order2 = new MedDispatchRec(2, null, null, reqs2, servicePoint);

        List<MedDispatchRec> requests = List.of(order1, order2);

        // Act
        DeliveryPlan plan = droneRoutingService.calcDeliveryPlan(requests);

        // Assert
        assertNotNull(plan);
        assertEquals(1, plan.dronePaths().size(), "Expected exactly one dronePath");

        DronePath dronePath = plan.dronePaths().getFirst();
        assertEquals(droneId, dronePath.droneId());

        List<DeliveryPath> deliveries = dronePath.deliveries();
        assertEquals(2, deliveries.size(), "Expected two deliveries for the drone");

        DeliveryPath d1 = deliveries.get(0);
        DeliveryPath d2 = deliveries.get(1);

        assertEquals(order1.id(), d1.deliveryId());
        assertEquals(order2.id(), d2.deliveryId());

        // Because origin == delivery locations and A* stops immediately,
        // all moves are just hovers; so there should be 0 moves in total.
        assertEquals(0, plan.totalMoves(), "Total moves should be 0 in this trivial case");

        // Cost = costInitial + costFinal + moves * costPerMove
        double expectedCost = capability.costInitial() + capability.costFinal();
        assertEquals(expectedCost, plan.totalCost(), 1e-9);

        // First delivery: segment from service point to delivery 1
        assertHoverPresent(d1.flightPath());
        assertEquals(servicePoint, d1.flightPath().getFirst());
        assertEquals(servicePoint, d1.flightPath().getLast());

        // Second delivery: from delivery 1 to delivery 2 / back to SP.
        // In this trivial case they are all the same point, but there still must be a hover.
        assertHoverPresent(d2.flightPath());
    }

    private void assertHoverPresent(List<LngLat> flightPath) {
        assertNotNull(flightPath);
        assertTrue(flightPath.size() >= 2, "FlightPath should have at least 2 points");

        boolean hoverFound = false;
        for (int i = 1; i < flightPath.size(); i++) {
            if (flightPath.get(i).equals(flightPath.get(i - 1))) {
                hoverFound = true;
                break;
            }
        }

        assertTrue(hoverFound, "Expected at least one hover (two identical consecutive LngLat)");
    }
}