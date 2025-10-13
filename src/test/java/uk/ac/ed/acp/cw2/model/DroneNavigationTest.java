package uk.ac.ed.acp.cw2.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class DroneNavigationTest {

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
            assertEquals(DroneNavigation.Direction16.E,   DroneNavigation.Direction16.angle_direction(0));
            assertEquals(DroneNavigation.Direction16.ENE, DroneNavigation.Direction16.angle_direction(22.5));
            assertEquals(DroneNavigation.Direction16.NE,  DroneNavigation.Direction16.angle_direction(45));
            assertEquals(DroneNavigation.Direction16.NNE, DroneNavigation.Direction16.angle_direction(67.5));
            assertEquals(DroneNavigation.Direction16.N,   DroneNavigation.Direction16.angle_direction(90));
            assertEquals(DroneNavigation.Direction16.NNW, DroneNavigation.Direction16.angle_direction(112.5));
            assertEquals(DroneNavigation.Direction16.NW,  DroneNavigation.Direction16.angle_direction(135));
            assertEquals(DroneNavigation.Direction16.WNW, DroneNavigation.Direction16.angle_direction(157.5));
            assertEquals(DroneNavigation.Direction16.W,   DroneNavigation.Direction16.angle_direction(180));
            assertEquals(DroneNavigation.Direction16.WSW, DroneNavigation.Direction16.angle_direction(202.5));
            assertEquals(DroneNavigation.Direction16.SW,  DroneNavigation.Direction16.angle_direction(225));
            assertEquals(DroneNavigation.Direction16.SSW, DroneNavigation.Direction16.angle_direction(247.5));
            assertEquals(DroneNavigation.Direction16.S,   DroneNavigation.Direction16.angle_direction(270));
            assertEquals(DroneNavigation.Direction16.SSE, DroneNavigation.Direction16.angle_direction(292.5));
            assertEquals(DroneNavigation.Direction16.SE,  DroneNavigation.Direction16.angle_direction(315));
            assertEquals(DroneNavigation.Direction16.ESE, DroneNavigation.Direction16.angle_direction(337.5));
        }

        @Test
        void angle_direction_360WrapsToE() {
            assertEquals(DroneNavigation.Direction16.E, DroneNavigation.Direction16.angle_direction(360));
        }

        @Test
        void angle_direction_invalidAngles_throwBadRequest() {
            assertThrows(ResponseStatusException.class, () -> DroneNavigation.Direction16.angle_direction(-0.1));
            assertThrows(ResponseStatusException.class, () -> DroneNavigation.Direction16.angle_direction(361));
            assertThrows(ResponseStatusException.class, () -> DroneNavigation.Direction16.angle_direction(15));
        }
    }

    @Nested
    class StepFromTests {
        @Test
        void stepFrom_movesExactlyOneStepAlongBearing() {
            var start = p(0, 0);

            // East (0°): +lng by STEP_SIZE, lat unchanged
            var e = DroneNavigation.Direction16.E.stepFrom(start);
            assertEquals(DroneNavigation.STEP_SIZE, e.getLng(), 1e-12);
            assertEquals(0.0, e.getLat(), 1e-12);
            assertEquals(DroneNavigation.STEP_SIZE, DroneNavigation.distance(start, e), 1e-12);

            // North (90°): +lat by STEP_SIZE, lng unchanged
            var n = DroneNavigation.Direction16.N.stepFrom(start);
            assertEquals(0.0, n.getLng(), 1e-12);
            assertEquals(DroneNavigation.STEP_SIZE, n.getLat(), 1e-12);
            assertEquals(DroneNavigation.STEP_SIZE, DroneNavigation.distance(start, n), 1e-12);

            // South-West (225°): distance is still STEP_SIZE
            var sw = DroneNavigation.Direction16.SW.stepFrom(start);
            assertEquals(DroneNavigation.STEP_SIZE, DroneNavigation.distance(start, sw), 1e-12);
        }

        @Test
        void stepFrom_composedDistanceIndependentOfDirection() {
            var start = p(0.01, -0.02);
            for (var dir : DroneNavigation.Direction16.values()) {
                var nxt = dir.stepFrom(start);
                var d = DroneNavigation.distance(start, nxt);
                assertEquals(DroneNavigation.STEP_SIZE, d, 1e-12);
            }
        }
    }
}