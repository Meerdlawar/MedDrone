package uk.ac.ed.acp.cw2.model;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.model.LngLat;
import java.util.Map;
import java.lang.Math;

public class Euclidian_distance {

    /** Step length and “hit” radius from the spec */
    public static final double STEP_SIZE = 0.00015; // Length of one move
    public static final double CLOSE_RADIUS = 0.00015; // Threshold of how far the drone can be

    public static double distance(LngLat a, LngLat b) {
        // returns the euclidian distance (pythagorean theorem)
        return Math.hypot(a.getLng() - b.getLng(), a.getLat() - b.getLat());
    }

    public static boolean isClose(LngLat a, LngLat b) {
        // returns true if less than the threshold
        return distance(a, b) < CLOSE_RADIUS;
    }


    // package uk.ac.ed.acp.cw2.geo;
    public enum Direction16 {
        E(0), ENE(22.5), NE(45), NNE(67.5),
        N(90), NNW(112.5), NW(135), WNW(157.5),
        W(180), WSW(202.5), SW(225), SSW(247.5),
        S(270), SSE(292.5), SE(315), ESE(337.5);

        public final double bearingDeg;
        Direction16(double bearingDeg) { this.bearingDeg = bearingDeg; }

        public static Direction16 nearestTo(double bearingDeg) {
            double b = ((bearingDeg % 360) + 360) % 360; // normalize [0,360)
            int idx = (int)Math.round(b / 22.5) % 16;
            return values()[idx];
        }

        public LngLat stepFrom(LngLat start, double step) {
            double rad = Math.toRadians(bearingDeg);
            double dx = Math.cos(rad) * step;
            double dy = Math.sin(rad) * step;
            return new LngLat(start.getLng() + dx, start.getLat() + dy);
        }
    }
}
