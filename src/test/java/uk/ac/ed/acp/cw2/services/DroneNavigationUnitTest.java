
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
            assertEquals(0.0, GeometryService.distance(a, a), EPS);
        }

        @Test
        void distance_basicPythagorean() {
            var a = p(0, 0);
            var b = p(3e-4, 4e-4);
            assertEquals(5e-4, GeometryService.distance(a, b), 1e-12);
        }

        @Test
        void distance_symmetry() {
            var a = p(0.1, 0.2);
            var b = p(-0.05, 0.0);
            assertEquals(GeometryService.distance(a, b), GeometryService.distance(b, a), EPS);
        }
    }

    @Nested
    class IsCloseTests {
        @Test
        void isClose_true_whenStrictlyLessThanCloseRadius() {
            var a = p(0, 0);
            var b = p(GeometryService.CLOSE_RADIUS * 0.999, 0);
            assertTrue(GeometryService.isClose(a, b));
        }

        @Test
        void isClose_false_whenExactlyOnCloseRadius() {
            var a = p(0, 0);
            var b = p(GeometryService.CLOSE_RADIUS, 0);
            assertFalse(GeometryService.isClose(a, b));
        }

        @Test
        void isClose_false_whenGreaterThanCloseRadius() {
            var a = p(0, 0);
            var b = p(GeometryService.CLOSE_RADIUS + 1e-6, 0);
            assertFalse(GeometryService.isClose(a, b));
        }
    }

    @Nested
    class DirectionAngleMappingTests {
        @Test
        void angle_direction_validMultiplesMapCorrectly() {
            assertEquals(Direction16.E,   GeometryService.angleToDirection(0));
            assertEquals(Direction16.ENE, GeometryService.angleToDirection(22.5));
            assertEquals(Direction16.NE,  GeometryService.angleToDirection(45));
            assertEquals(Direction16.NNE, GeometryService.angleToDirection(67.5));
            assertEquals(Direction16.N,   GeometryService.angleToDirection(90));
            assertEquals(Direction16.NNW, GeometryService.angleToDirection(112.5));
            assertEquals(Direction16.NW,  GeometryService.angleToDirection(135));
            assertEquals(Direction16.WNW, GeometryService.angleToDirection(157.5));
            assertEquals(Direction16.W,   GeometryService.angleToDirection(180));
            assertEquals(Direction16.WSW, GeometryService.angleToDirection(202.5));
            assertEquals(Direction16.SW,  GeometryService.angleToDirection(225));
            assertEquals(Direction16.SSW, GeometryService.angleToDirection(247.5));
            assertEquals(Direction16.S,   GeometryService.angleToDirection(270));
            assertEquals(Direction16.SSE, GeometryService.angleToDirection(292.5));
            assertEquals(Direction16.SE,  GeometryService.angleToDirection(315));
            assertEquals(Direction16.ESE, GeometryService.angleToDirection(337.5));
        }

        @Test
        void angle_direction_360WrapsToE() {
            assertEquals(Direction16.E, GeometryService.angleToDirection(360));
        }

        @Test
        void angle_direction_invalidAngles_throwBadRequest() {
            assertThrows(ResponseStatusException.class, () -> GeometryService.angleToDirection(-0.1));
            assertThrows(ResponseStatusException.class, () -> GeometryService.angleToDirection(361));
            assertThrows(ResponseStatusException.class, () -> GeometryService.angleToDirection(15));
        }
    }

    @Nested
    class StepFromTests {
        @Test
        void stepFrom_movesExactlyOneStepAlongBearing() {
            var start = p(0, 0);

            // East (0°): +lng by STEP_SIZE, lat unchanged
            var e = GeometryService.stepFrom(start, Direction16.E);
            assertEquals(0.00015, e.lng(), 1e-12);
            assertEquals(0.0, e.lat(), 1e-12);
            assertEquals(0.00015, GeometryService.distance(start, e), 1e-12);

            // North (90°): +lat by STEP_SIZE, lng unchanged
            var n = GeometryService.stepFrom(start, Direction16.N);
            assertEquals(0.0, n.lng(), 1e-12);
            assertEquals(0.00015, n.lat(), 1e-12);
            assertEquals(0.00015, GeometryService.distance(start, n), 1e-12);

            // South-West (225°): distance is still STEP_SIZE
            var sw = GeometryService.stepFrom(start, Direction16.SW);
            assertEquals(0.00015, GeometryService.distance(start, sw), 1e-12);
        }

        @Test
        void stepFrom_composedDistanceIndependentOfDirection() {
            var start = p(0.01, -0.02);
            for (var dir : Direction16.values()) {
                var nxt = GeometryService.stepFrom(start, dir);
                var d = GeometryService.distance(start, nxt);
                assertEquals(0.00015, d, 1e-12);
            }
        }
    }
}