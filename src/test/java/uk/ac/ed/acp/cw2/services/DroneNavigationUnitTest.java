
package uk.ac.ed.acp.cw2.services;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.ed.acp.cw2.data.Directions.Direction16;
import uk.ac.ed.acp.cw2.dto.LngLat;

import static org.junit.jupiter.api.Assertions.*;

class DroneNavigationUnitTest {

    private static LngLat p(double lng, double lat) { return new LngLat(lng, lat); }

    // Tolerance as per the specification
    private static final double EPS = 1e-12;

    @Nested
    class DistanceTests {
        @Test
        void distance_zeroWhenSamePoint() {
            var a = p(-3.192, 55.946);
            assertEquals(0.0, DroneNavigation.distance(a, a), EPS);
        }

        @Test
        void distance_basicPythagorean() {
            var a = p(0, 0);
            var b = p(3e-4, 4e-4);
            assertEquals(5e-4, DroneNavigation.distance(a, b), 1e-12);
        }

        @Test
        void distance_symmetry() {
            var a = p(0.1, 0.2);
            var b = p(-0.05, 0.0);
            assertEquals(DroneNavigation.distance(a, b), DroneNavigation.distance(b, a), EPS);
        }
    }

    @Nested
    class IsCloseTests {
        @Test
        void isClose_true_whenStrictlyLessThanCloseRadius() {
            var a = p(0, 0);
            var b = p(DroneNavigation.CLOSE_RADIUS * 0.999, 0);
            assertTrue(DroneNavigation.isClose(a, b));
        }

        @Test
        void isClose_false_whenExactlyOnCloseRadius() {
            var a = p(0, 0);
            var b = p(DroneNavigation.CLOSE_RADIUS, 0);
            assertFalse(DroneNavigation.isClose(a, b));
        }

        @Test
        void isClose_false_whenGreaterThanCloseRadius() {
            var a = p(0, 0);
            var b = p(DroneNavigation.CLOSE_RADIUS + 1e-6, 0);
            assertFalse(DroneNavigation.isClose(a, b));
        }
    }

    @Nested
    class DirectionAngleMappingTests {
        @Test
        void angle_direction_validMultiplesMapCorrectly() {
            assertEquals(Direction16.E,   DroneNavigation.angleToDirection(0));
            assertEquals(Direction16.ENE, DroneNavigation.angleToDirection(22.5));
            assertEquals(Direction16.NE,  DroneNavigation.angleToDirection(45));
            assertEquals(Direction16.NNE, DroneNavigation.angleToDirection(67.5));
            assertEquals(Direction16.N,   DroneNavigation.angleToDirection(90));
            assertEquals(Direction16.NNW, DroneNavigation.angleToDirection(112.5));
            assertEquals(Direction16.NW,  DroneNavigation.angleToDirection(135));
            assertEquals(Direction16.WNW, DroneNavigation.angleToDirection(157.5));
            assertEquals(Direction16.W,   DroneNavigation.angleToDirection(180));
            assertEquals(Direction16.WSW, DroneNavigation.angleToDirection(202.5));
            assertEquals(Direction16.SW,  DroneNavigation.angleToDirection(225));
            assertEquals(Direction16.SSW, DroneNavigation.angleToDirection(247.5));
            assertEquals(Direction16.S,   DroneNavigation.angleToDirection(270));
            assertEquals(Direction16.SSE, DroneNavigation.angleToDirection(292.5));
            assertEquals(Direction16.SE,  DroneNavigation.angleToDirection(315));
            assertEquals(Direction16.ESE, DroneNavigation.angleToDirection(337.5));
        }

        @Test
        void angle_direction_360WrapsToE() {
            assertEquals(Direction16.E, DroneNavigation.angleToDirection(360));
        }

        @Test
        void angle_direction_invalidAngles_throwBadRequest() {
            assertThrows(ResponseStatusException.class, () -> DroneNavigation.angleToDirection(-0.1));
            assertThrows(ResponseStatusException.class, () -> DroneNavigation.angleToDirection(361));
            assertThrows(ResponseStatusException.class, () -> DroneNavigation.angleToDirection(15));
        }
    }

    @Nested
    class StepFromTests {
        @Test
        void stepFrom_movesExactlyOneStepAlongBearing() {
            var start = p(0, 0);

            // East (0°): +lng by STEP_SIZE, lat unchanged
            var e = DroneNavigation.stepFrom(start, Direction16.E);
            assertEquals(0.00015, e.lng(), 1e-12);
            assertEquals(0.0, e.lat(), 1e-12);
            assertEquals(0.00015, DroneNavigation.distance(start, e), 1e-12);

            // North (90°): +lat by STEP_SIZE, lng unchanged
            var n = DroneNavigation.stepFrom(start, Direction16.N);
            assertEquals(0.0, n.lng(), 1e-12);
            assertEquals(0.00015, n.lat(), 1e-12);
            assertEquals(0.00015, DroneNavigation.distance(start, n), 1e-12);

            // South-West (225°): distance is still STEP_SIZE
            var sw = DroneNavigation.stepFrom(start, Direction16.SW);
            assertEquals(0.00015, DroneNavigation.distance(start, sw), 1e-12);
        }

        @Test
        void stepFrom_composedDistanceIndependentOfDirection() {
            var start = p(0.01, -0.02);
            for (var dir : Direction16.values()) {
                var nxt = DroneNavigation.stepFrom(start, dir);
                var d = DroneNavigation.distance(start, nxt);
                assertEquals(0.00015, d, 1e-12);
            }
        }
    }
}