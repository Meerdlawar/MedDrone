package uk.ac.ed.acp.cw2.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.ed.acp.cw2.data.LngLat;

import java.awt.geom.Path2D;
import java.util.List;
import java.util.Objects;

/** Point-in-polygon helper using Path2D (even-odd rule). */
public final class PointInRegion {
    // Very small geometric tolerance (in degrees) for border checks.
    // (Spec tolerates tiny floating errors)
    private static final double EPS = 1e-12;

    private PointInRegion() {}

    /**
     * Returns true if (lng,lat) is inside the closed polygon (including the border).
     * @param point  query point
     * @param vertices closed polygon where vertices.get(0).equals(vertices.get(last))
     * @throws ResponseStatusException(400) if the polygon is null, has <4 points, or is not closed
     */
    public static boolean isInRegion(LngLat point, List<LngLat> vertices) {
        validateClosedPolygon(vertices);

        // Path2D library doesn't count the border as inside a polygon
        // so manual check needed
        for (int i = 0; i < vertices.size() - 1; i++) { // iterate edges v[i] -> v[i+1]
            LngLat a = vertices.get(i);
            LngLat b = vertices.get(i + 1);
            if (onSegment(point.getLng(), point.getLat(), a.getLng(), a.getLat(), b.getLng(), b.getLat())) {
                return true;
            }
        }

        // Ray casting: Interior check with even-odd rule
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        LngLat first = vertices.get(0);
        path.moveTo(first.getLng(), first.getLat());
        for (int i = 1; i < vertices.size(); i++) {
            LngLat v = vertices.get(i);
            path.lineTo(v.getLng(), v.getLat());
        }
        // Path is already closed by the repeated last vertex.

        return path.contains(point.getLng(), point.getLat());
    }

    /** Ensures polygon is non-null, has at least 4 points, and is closed (first equals last). */
    private static void validateClosedPolygon(List<LngLat> vertices) {
        if (vertices == null || vertices.size() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Region must be a closed polygon");
        }
        LngLat first = vertices.get(0);
        LngLat last  = vertices.get(vertices.size() - 1);
        if (!equalCoords(first, last)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Region must be closed (last vertex repeats first)");
        }
    }

    private static boolean equalCoords(LngLat a, LngLat b) {
        return a != null && b != null
                && Objects.equals(a.getLng(), b.getLng())
                && Objects.equals(a.getLat(), b.getLat());
    }


    /** True if point P is on segment AB within EPS. */
    private static boolean onSegment(double px, double py,
                                     double ax, double ay,
                                     double bx, double by) {
        // First check if point is within the bounding box of the segment
        double minx = Math.min(ax, bx) - EPS, maxx = Math.max(ax, bx) + EPS;
        double miny = Math.min(ay, by) - EPS, maxy = Math.max(ay, by) + EPS;
        if (px < minx || px > maxx || py < miny || py > maxy) {
            return false;
        }

        // Check for a degenerate segment (A ≈ B)
        double segmentLength = Math.hypot(bx - ax, by - ay);
        if (segmentLength <= EPS) {
            // Degenerate segment: A≈B, check distance from P to A
            return Math.hypot(px - ax, py - ay) <= EPS;
        }

        // Check collinearity using cross-product
        // Cross product: (B - A) × (P - A)
        // If cross-product is 0, points are collinear
        double crossProduct = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        double crossProductMagnitude = Math.abs(crossProduct);

        // Normalize by segment length to get perpendicular distance
        double perpendicularDistance = crossProductMagnitude / segmentLength;

        // Point is on the line segment if it's collinear (distance ~0)
        // and within the bounding box (already checked above)
        return perpendicularDistance <= EPS;
    }
}
