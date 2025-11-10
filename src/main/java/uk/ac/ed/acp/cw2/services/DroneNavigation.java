package uk.ac.ed.acp.cw2.services;


import java.lang.Math;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.ed.acp.cw2.data.LngLat;

@Service
public class DroneNavigation {

    /** Step length and “hit” radius from the spec */
    public static final double STEP_SIZE = 0.00015; // Length of one move
    public static final double CLOSE_RADIUS = 0.00015; // Threshold of how far the drone can be

    public static double distance(LngLat a, LngLat b) {
        // returns the euclidian distance (pythagorean theorem)
        return Math.hypot(a.lng() - b.lng(), a.lat() - b.lat());
    }

    public static boolean isClose(LngLat a, LngLat b) {
        // returns true if less than the threshold
        return distance(a, b) < CLOSE_RADIUS;
    }


    public enum Direction16 {
        E(0), ENE(22.5), NE(45), NNE(67.5),
        N(90), NNW(112.5), NW(135), WNW(157.5),
        W(180), WSW(202.5), SW(225), SSW(247.5),
        S(270), SSE(292.5), SE(315), ESE(337.5);

        public final double bearingDeg;
        Direction16(double bearingDeg) { this.bearingDeg = bearingDeg; }

        public static Direction16 angle_direction(double bearingDeg) {
            // filter invalid data
            if (bearingDeg < 0 || bearingDeg > 360 || bearingDeg % 22.5 != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Angle must be one of {0,22.5,45,...,337.5}");
            }
            double normVal = (bearingDeg % 360); // incase 360 is, input it would map to E(0)
            int idx = (int) (normVal / 22.5) % 16;
            return values()[idx];
        }

        public LngLat stepFrom(LngLat start) {
            double rad = Math.toRadians(bearingDeg);
            double dx = Math.cos(rad) * STEP_SIZE;
            double dy = Math.sin(rad) * STEP_SIZE;
            return new LngLat(start.lng() + dx, start.lat() + dy);
        }
    }
}
