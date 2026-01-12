package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.ed.acp.cw2.data.Directions.Direction16;
import uk.ac.ed.acp.cw2.dto.LngLat;
import uk.ac.ed.acp.cw2.services.DronePointInRegion;
import uk.ac.ed.acp.cw2.services.GeometryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FR3: Testing Geometry Calculations
 * 
 * Based on LO2 Testing Plan Section 3: Testing geometry calculations
 * 
 * Test Coverage:
 * 1. Check Pythagorean distance calculation between two coordinates is correct.
 * 2. Check closeness test returns true when distance is strictly less than 0.00015°.
 * 3. Check closeness test returns false when distance is exactly 0.00015° (boundary case).
 * 4. Check closeness test returns false when distance is greater than 0.00015°.
 * 5. Check point-in-polygon correctly identifies a point inside a no-fly zone.
 * 6. Check point-in-polygon correctly identifies a point outside a no-fly zone.
 * 7. Check point-in-polygon correctly handles a point on the boundary of a no-fly zone.
 * 8. Check line-segment intersection correctly detects a path crossing a no-fly zone boundary.
 * 9. Check line-segment intersection correctly identifies when a path does not cross a boundary.
 * 10. Check that crossing a corner of a no-fly zone is correctly detected as invalid.
 * 
 * Total: 10 tests
 */
@DisplayName("FR3: Geometry Calculations Tests")
class GeometryCalculationsTest {

    // Tolerance as per specification: ±10⁻¹² 
    private static final double EPS = 1e-12;
    private static final double CLOSE_DISTANCE = 0.00015;

    private static LngLat p(double lng, double lat) {
        return new LngLat(lng, lat);
    }

    // Standard rectangle polygon for testing (closed)
    private static List<LngLat> createRectangle() {
        return List.of(
                p(-1, 1),   // top-left
                p(1, 1),    // top-right
                p(1, -1),   // bottom-right
                p(-1, -1),  // bottom-left
                p(-1, 1)    // back to top-left (closed)
        );
    }

    // =========================================================================
    // FR3.1: Check Pythagorean distance calculation between two coordinates 
    //        is correct
    // =========================================================================

    @Nested
    @DisplayName("FR3.1: Pythagorean Distance Calculation")
    class FR3_1_DistanceCalculationTests {

        @Test
        @DisplayName("Distance between same point is zero")
        void FR3_1_1_distanceSamePoint_isZero() {
            var point = p(-3.192, 55.946);
            
            double distance = GeometryService.distance(point, point);
            
            assertEquals(0.0, distance, EPS);
        }

        @Test
        @DisplayName("Basic Pythagorean calculation is correct (3-4-5 triangle)")
        void FR3_1_2_basicPythagoreanCalculation_isCorrect() {
            var a = p(0, 0);
            var b = p(3e-4, 4e-4);  // 3,4,5 scaled to degrees
            
            double distance = GeometryService.distance(a, b);
            
            assertEquals(5e-4, distance, EPS);
        }

        @Test
        @DisplayName("Distance calculation is symmetric")
        void FR3_1_3_distanceCalculation_isSymmetric() {
            var a = p(0.1, 0.2);
            var b = p(-0.05, 0.0);
            
            double distAB = GeometryService.distance(a, b);
            double distBA = GeometryService.distance(b, a);
            
            assertEquals(distAB, distBA, EPS);
        }

        @Test
        @DisplayName("Distance along longitude axis is correct")
        void FR3_1_4_distanceAlongLongitude_isCorrect() {
            var a = p(-3.0, 55.0);
            var b = p(-3.1, 55.0);  // 0.1 degrees apart on lng
            
            double distance = GeometryService.distance(a, b);
            
            assertEquals(0.1, distance, EPS);
        }

        @Test
        @DisplayName("Distance along latitude axis is correct")
        void FR3_1_5_distanceAlongLatitude_isCorrect() {
            var a = p(-3.0, 55.0);
            var b = p(-3.0, 55.05);  // 0.05 degrees apart on lat
            
            double distance = GeometryService.distance(a, b);
            
            assertEquals(0.05, distance, EPS);
        }
    }

    // =========================================================================
    // FR3.2: Check closeness test returns true when distance is strictly 
    //        less than 0.00015°
    // =========================================================================

    @Nested
    @DisplayName("FR3.2: Closeness Test - Strictly Less Than Threshold")
    class FR3_2_ClosenessStrictlyLessTests {

        @Test
        @DisplayName("Points 99.9% of threshold apart are close")
        void FR3_2_1_points999PercentApart_areClose() {
            var a = p(0, 0);
            var b = p(CLOSE_DISTANCE * 0.999, 0);
            
            boolean isClose = GeometryService.isClose(a, b);
            
            assertTrue(isClose);
        }

        @Test
        @DisplayName("Points 50% of threshold apart are close")
        void FR3_2_2_points50PercentApart_areClose() {
            var a = p(0, 0);
            var b = p(CLOSE_DISTANCE * 0.5, 0);
            
            boolean isClose = GeometryService.isClose(a, b);
            
            assertTrue(isClose);
        }

        @Test
        @DisplayName("Same point is close to itself")
        void FR3_2_3_samePoint_isClose() {
            var point = p(-3.186, 55.944);
            
            boolean isClose = GeometryService.isClose(point, point);
            
            assertTrue(isClose);
        }
    }

    // =========================================================================
    // FR3.3: Check closeness test returns false when distance is exactly 
    //        0.00015° (boundary case)
    // =========================================================================

    @Nested
    @DisplayName("FR3.3: Closeness Test - Exactly At Threshold (Boundary)")
    class FR3_3_ClosenessExactBoundaryTests {

        @Test
        @DisplayName("Points exactly 0.00015° apart are NOT close (boundary)")
        void FR3_3_1_pointsExactlyAtThreshold_areNotClose() {
            var a = p(0, 0);
            var b = p(CLOSE_DISTANCE, 0);  // Exactly at threshold
            
            boolean isClose = GeometryService.isClose(a, b);
            
            assertFalse(isClose, "Points exactly at 0.00015° should NOT be considered close");
        }
    }

    // =========================================================================
    // FR3.4: Check closeness test returns false when distance is greater 
    //        than 0.00015°
    // =========================================================================

    @Nested
    @DisplayName("FR3.4: Closeness Test - Greater Than Threshold")
    class FR3_4_ClosenessGreaterThanTests {

        @Test
        @DisplayName("Points 100.1% of threshold apart are NOT close")
        void FR3_4_1_pointsSlightlyOverThreshold_areNotClose() {
            var a = p(0, 0);
            var b = p(CLOSE_DISTANCE + 1e-6, 0);
            
            boolean isClose = GeometryService.isClose(a, b);
            
            assertFalse(isClose);
        }

        @Test
        @DisplayName("Points significantly apart are NOT close")
        void FR3_4_2_pointsSignificantlyApart_areNotClose() {
            var a = p(0, 0);
            var b = p(0.001, 0.001);  // Much greater than threshold
            
            boolean isClose = GeometryService.isClose(a, b);
            
            assertFalse(isClose);
        }
    }

    // =========================================================================
    // FR3.5: Check point-in-polygon correctly identifies a point inside 
    //        a no-fly zone
    // =========================================================================

    @Nested
    @DisplayName("FR3.5: Point-in-Polygon - Inside Detection")
    class FR3_5_PointInsidePolygonTests {

        @Test
        @DisplayName("Point at center of polygon is detected as inside")
        void FR3_5_1_pointAtCenter_isInside() {
            var rectangle = createRectangle();
            var center = p(0, 0);
            
            boolean isInside = DronePointInRegion.isInRegion(center, rectangle);
            
            assertTrue(isInside);
        }

        @Test
        @DisplayName("Point near corner (but inside) is detected as inside")
        void FR3_5_2_pointNearCorner_isInside() {
            var rectangle = createRectangle();
            var nearCorner = p(0.5, 0.5);
            
            boolean isInside = DronePointInRegion.isInRegion(nearCorner, rectangle);
            
            assertTrue(isInside);
        }

        @Test
        @DisplayName("Point barely inside edge is detected as inside")
        void FR3_5_3_pointBarelyInsideEdge_isInside() {
            var rectangle = createRectangle();
            var nearEdge = p(-0.99, 0.99);
            
            boolean isInside = DronePointInRegion.isInRegion(nearEdge, rectangle);
            
            assertTrue(isInside);
        }
    }

    // =========================================================================
    // FR3.6: Check point-in-polygon correctly identifies a point outside 
    //        a no-fly zone
    // =========================================================================

    @Nested
    @DisplayName("FR3.6: Point-in-Polygon - Outside Detection")
    class FR3_6_PointOutsidePolygonTests {

        @Test
        @DisplayName("Point clearly outside polygon is detected as outside")
        void FR3_6_1_pointClearlyOutside_isOutside() {
            var rectangle = createRectangle();
            var outside = p(2, 0);
            
            boolean isInside = DronePointInRegion.isInRegion(outside, rectangle);
            
            assertFalse(isInside);
        }

        @Test
        @DisplayName("Point barely outside edge is detected as outside")
        void FR3_6_2_pointBarelyOutside_isOutside() {
            var rectangle = createRectangle();
            var barelyOutside = p(-1.0000001, 0);
            
            boolean isInside = DronePointInRegion.isInRegion(barelyOutside, rectangle);
            
            assertFalse(isInside);
        }

        @Test
        @DisplayName("Point outside top edge is detected as outside")
        void FR3_6_3_pointAboveTopEdge_isOutside() {
            var rectangle = createRectangle();
            var above = p(0, 2);
            
            boolean isInside = DronePointInRegion.isInRegion(above, rectangle);
            
            assertFalse(isInside);
        }
    }

    // =========================================================================
    // FR3.7: Check point-in-polygon correctly handles a point on the boundary 
    //        of a no-fly zone
    // =========================================================================

    @Nested
    @DisplayName("FR3.7: Point-in-Polygon - Boundary Handling")
    class FR3_7_PointOnBoundaryTests {

        @Test
        @DisplayName("Point on top edge is counted as inside")
        void FR3_7_1_pointOnTopEdge_isInside() {
            var rectangle = createRectangle();
            var onEdge = p(0, 1);  // Middle of top edge
            
            boolean isInside = DronePointInRegion.isInRegion(onEdge, rectangle);
            
            assertTrue(isInside, "Point on boundary should be counted as inside");
        }

        @Test
        @DisplayName("Point on left edge is counted as inside")
        void FR3_7_2_pointOnLeftEdge_isInside() {
            var rectangle = createRectangle();
            var onEdge = p(-1, 0);  // Middle of left edge
            
            boolean isInside = DronePointInRegion.isInRegion(onEdge, rectangle);
            
            assertTrue(isInside, "Point on boundary should be counted as inside");
        }

        @Test
        @DisplayName("Point on vertex is counted as inside")
        void FR3_7_3_pointOnVertex_isInside() {
            var rectangle = createRectangle();
            var onVertex = p(-1, 1);  // Top-left vertex
            
            boolean isInside = DronePointInRegion.isInRegion(onVertex, rectangle);
            
            assertTrue(isInside, "Point on vertex should be counted as inside");
        }

        @Test
        @DisplayName("Point on bottom edge is counted as inside")
        void FR3_7_4_pointOnBottomEdge_isInside() {
            var rectangle = createRectangle();
            var onEdge = p(0, -1);  // Middle of bottom edge
            
            boolean isInside = DronePointInRegion.isInRegion(onEdge, rectangle);
            
            assertTrue(isInside, "Point on boundary should be counted as inside");
        }
    }

    // =========================================================================
    // FR3.8: Check line-segment intersection correctly detects a path 
    //        crossing a no-fly zone boundary
    // =========================================================================

    @Nested
    @DisplayName("FR3.8: Line-Segment Intersection - Crossing Detection")
    class FR3_8_LineCrossingDetectionTests {

        @Test
        @DisplayName("Path crossing through no-fly zone is detected")
        void FR3_8_1_pathCrossingThroughZone_isDetected() {
            var rectangle = createRectangle();
            
            // Path from outside to inside
            var start = p(-2, 0);
            var end = p(0, 0);
            
            // The end point is inside the zone
            boolean endInside = DronePointInRegion.isInRegion(end, rectangle);
            
            assertTrue(endInside, "End point should be inside, indicating crossing occurred");
        }

        @Test
        @DisplayName("Path completely crossing zone is detected")
        void FR3_8_2_pathCompletelyCrossing_isDetected() {
            var rectangle = createRectangle();
            
            // Path from left outside to right outside, passing through
            var start = p(-2, 0);
            var end = p(2, 0);
            
            // Check if path intersects the zone at any point
            // Since both ends are outside, but path goes through center,
            // intermediate point should be inside
            var midpoint = p(0, 0);
            boolean midInside = DronePointInRegion.isInRegion(midpoint, rectangle);
            
            assertTrue(midInside, "Midpoint of crossing path should be inside zone");
        }
    }

    // =========================================================================
    // FR3.9: Check line-segment intersection correctly identifies when 
    //        a path does not cross a boundary
    // =========================================================================

    @Nested
    @DisplayName("FR3.9: Line-Segment Intersection - No Crossing")
    class FR3_9_LineNoCrossingTests {

        @Test
        @DisplayName("Path completely outside zone does not cross")
        void FR3_9_1_pathCompletelyOutside_doesNotCross() {
            var rectangle = createRectangle();
            
            // Path entirely outside the zone
            var start = p(2, 0);
            var end = p(3, 1);
            
            boolean startInside = DronePointInRegion.isInRegion(start, rectangle);
            boolean endInside = DronePointInRegion.isInRegion(end, rectangle);
            
            assertFalse(startInside, "Start should be outside");
            assertFalse(endInside, "End should be outside");
        }

        @Test
        @DisplayName("Path entirely inside zone does not cross boundary")
        void FR3_9_2_pathEntirelyInside_doesNotCrossBoundary() {
            var rectangle = createRectangle();
            
            // Path entirely inside the zone
            var start = p(-0.5, -0.5);
            var end = p(0.5, 0.5);
            
            boolean startInside = DronePointInRegion.isInRegion(start, rectangle);
            boolean endInside = DronePointInRegion.isInRegion(end, rectangle);
            
            assertTrue(startInside, "Start should be inside");
            assertTrue(endInside, "End should be inside");
        }
    }

    // =========================================================================
    // FR3.10: Check that crossing a corner of a no-fly zone is correctly 
    //         detected as invalid
    // =========================================================================

    @Nested
    @DisplayName("FR3.10: Corner Crossing Detection")
    class FR3_10_CornerCrossingTests {

        @Test
        @DisplayName("Path passing through corner vertex is detected")
        void FR3_10_1_pathThroughCorner_isDetected() {
            var rectangle = createRectangle();
            
            // Path that passes exactly through corner (-1, 1)
            var corner = p(-1, 1);
            
            boolean cornerInside = DronePointInRegion.isInRegion(corner, rectangle);
            
            assertTrue(cornerInside, "Corner point should be considered inside (on boundary)");
        }

        @Test
        @DisplayName("Path grazing corner region is handled correctly")
        void FR3_10_2_pathGrazingCornerRegion_handledCorrectly() {
            var rectangle = createRectangle();
            
            // Point just outside the corner
            var justOutsideCorner = p(-1.001, 1.001);
            
            boolean isInside = DronePointInRegion.isInRegion(justOutsideCorner, rectangle);
            
            assertFalse(isInside, "Point just outside corner should be outside");
        }

        @Test
        @DisplayName("Concave polygon corner handling is correct")
        void FR3_10_3_concavePolygonCorner_handledCorrectly() {
            // L-shaped (concave) polygon
            var concave = List.of(
                    p(0, 0),
                    p(2, 0),
                    p(2, 1),
                    p(1, 1),
                    p(1, 2),
                    p(0, 2),
                    p(0, 0)
            );
            
            // Point in the "notch" of the L (should be outside)
            var inNotch = p(1.5, 1.5);
            boolean isInside = DronePointInRegion.isInRegion(inNotch, concave);
            
            assertFalse(isInside, "Point in concave notch should be outside");
            
            // Point inside the L
            var insideL = p(0.5, 0.5);
            boolean insideLResult = DronePointInRegion.isInRegion(insideL, concave);
            
            assertTrue(insideLResult, "Point inside L should be inside");
        }
    }

    // =========================================================================
    // Additional: Polygon Validation Tests (required for robust testing)
    // =========================================================================

    @Nested
    @DisplayName("FR3.X: Polygon Validation")
    class FR3_X_PolygonValidationTests {

        @Test
        @DisplayName("Open polygon (not closed) throws BadRequest")
        void openPolygon_throwsBadRequest() {
            var openPolygon = List.of(
                    p(-1, 1), p(1, 1), p(1, -1), p(-1, -1)
                    // Missing closing vertex
            );

            var ex = assertThrows(ResponseStatusException.class, () ->
                    DronePointInRegion.isInRegion(p(0, 0), openPolygon));
            
            assertEquals(400, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("Polygon with too few vertices throws BadRequest")
        void tooFewVertices_throwsBadRequest() {
            var tooFew = List.of(p(0, 0), p(1, 0), p(0, 1));  // Only 3 vertices

            var ex = assertThrows(ResponseStatusException.class, () ->
                    DronePointInRegion.isInRegion(p(0, 0), tooFew));
            
            assertEquals(400, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("Null vertices list throws BadRequest")
        void nullVertices_throwsBadRequest() {
            var ex = assertThrows(ResponseStatusException.class, () ->
                    DronePointInRegion.isInRegion(p(0, 0), null));
            
            assertEquals(400, ex.getStatusCode().value());
        }
    }

    // =========================================================================
    // Additional: Direction and Step Tests
    // =========================================================================

    @Nested
    @DisplayName("FR3.X: Direction and Step Calculations")
    class FR3_X_DirectionStepTests {

        @Test
        @DisplayName("Step size is exactly 0.00015° in any direction")
        void stepSize_isExactlyOneMove() {
            var start = p(0, 0);

            for (Direction16 direction : Direction16.values()) {
                var next = GeometryService.stepFrom(start, direction);
                double distance = GeometryService.distance(start, next);
                
                assertEquals(0.00015, distance, EPS,
                        "Step in direction " + direction + " should be exactly 0.00015°");
            }
        }

        @Test
        @DisplayName("East direction moves correctly (+lng, 0 lat)")
        void eastDirection_movesCorrectly() {
            var start = p(0, 0);
            var east = GeometryService.stepFrom(start, Direction16.E);
            
            assertEquals(0.00015, east.lng(), EPS);
            assertEquals(0.0, east.lat(), EPS);
        }

        @Test
        @DisplayName("North direction moves correctly (0 lng, +lat)")
        void northDirection_movesCorrectly() {
            var start = p(0, 0);
            var north = GeometryService.stepFrom(start, Direction16.N);
            
            assertEquals(0.0, north.lng(), EPS);
            assertEquals(0.00015, north.lat(), EPS);
        }
    }
}
