package uk.ac.ed.acp.cw2.services;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.ed.acp.cw2.data.LngLat;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PointInRegionUnitTest {


    private static LngLat p(double lng, double lat) { return new LngLat(lng, lat); }


    private static List<LngLat> rectClosed() {
        return List.of(
                p(-1, 1),   // top-left
                p(1, 1),    // top-right
                p(1, -1),   // bottom-right
                p(-1, -1),  // bottom-left
                p(-1, 1)    // back to top-left (closed)
        );
    }

    // open rectangle
    private static List<LngLat> rectOpen() {
        return List.of(
                p(-1, 1),
                p(1, 1),
                p(1, -1),
                p(-1, -1)
                // no closing vertex
        );
    }

    @Test
    void insideRectangle_returnsTrue() {
        var verts = rectClosed();
        assertTrue(PointInRegion.isInRegion(p(0, 0), verts));
        assertTrue(PointInRegion.isInRegion(p(0.5, 0.5), verts));
        assertTrue(PointInRegion.isInRegion(p(-0.99, 0.99), verts));
    }

    @Test
    void outsideRectangle_returnsFalse() {
        var verts = rectClosed();
        assertFalse(PointInRegion.isInRegion(p(2, 0), verts));
        assertFalse(PointInRegion.isInRegion(p(0, 2), verts));
        assertFalse(PointInRegion.isInRegion(p(-1.0000001, 0), verts));
        assertFalse(PointInRegion.isInRegion(p(0, -1.0000001), verts));
    }

    @Test
    void onEdge_countsAsInside() {
        var verts = rectClosed();
        // Middle of top edge y=1 from (-1,1) to (1,1)
        assertTrue(PointInRegion.isInRegion(p(0, 1), verts));
        // Middle of left edge x=-1
        assertTrue(PointInRegion.isInRegion(p(-1, 0), verts));
        // Middle of bottom edge y=-1
        assertTrue(PointInRegion.isInRegion(p(0, -1), verts));
        // Middle of right edge x=1
        assertTrue(PointInRegion.isInRegion(p(1, 0), verts));
    }

    @Test
    void onVertex_countsAsInside() {
        var verts = rectClosed();
        assertTrue(PointInRegion.isInRegion(p(-1, 1), verts));   // top-left
        assertTrue(PointInRegion.isInRegion(p(1, 1), verts));    // top-right
        assertTrue(PointInRegion.isInRegion(p(1, -1), verts));   // bottom-right
        assertTrue(PointInRegion.isInRegion(p(-1, -1), verts));  // bottom-left
    }

    @Test
    void triangle_concaveLikeCases_basicInsideOutside() {
        // Simple triangle (closed)
        var tri = List.of(
                p(0, 0),
                p(2, 0),
                p(1, 2),
                p(0, 0)
        );
        assertTrue(PointInRegion.isInRegion(p(1, 0.5), tri));   // inside
        assertFalse(PointInRegion.isInRegion(p(-0.1, 0.1), tri)); // outside


        var concave = List.of(
                p(0, 0),
                p(2, 1),
                p(0, 2),
                p(0.5, 1),
                p(0, 0)
        );
        assertTrue(PointInRegion.isInRegion(p(0.9, 1), concave));   // inside
        assertFalse(PointInRegion.isInRegion(p(1.5, 1.5), concave)); // outside (in the "dent")
    }

    @Test
    void openPolygon_throwsBadRequest() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> PointInRegion.isInRegion(p(0, 0), rectOpen()));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().toLowerCase().contains("closed"));
    }

    @Test
    void tooFewVertices_throwsBadRequest() {
        var tooFew = List.of(p(0,0), p(1,0), p(0,1)); // 3 points only
        var ex = assertThrows(ResponseStatusException.class,
                () -> PointInRegion.isInRegion(p(0, 0), tooFew));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void nullVertices_throwsBadRequest() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> PointInRegion.isInRegion(p(0, 0), null));
        assertEquals(400, ex.getStatusCode().value());
    }

}