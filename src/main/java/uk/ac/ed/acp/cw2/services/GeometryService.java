package uk.ac.ed.acp.cw2.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.ed.acp.cw2.dto.LngLat;
import static java.lang.Math.*;
import static uk.ac.ed.acp.cw2.data.Directions.STEP_SIZE;
import uk.ac.ed.acp.cw2.data.Directions.Direction16;

@Service
public class GeometryService {

    // Step length and “hit” radius from the spec
    public static final double CLOSE_RADIUS = 0.00015;

    public static double distance(LngLat a, LngLat b) {
        // returns the euclidian distance (pythagorean theorem)
        return Math.hypot(a.lng() - b.lng(), a.lat() - b.lat());
    }

    public static boolean isClose(LngLat a, LngLat b) {
        // returns true if less than the threshold
        return distance(a, b) < CLOSE_RADIUS;
    }

    // angle -> Direction16
    public static Direction16 angleToDirection(double bearingDeg) {
        if (bearingDeg < 0 || bearingDeg > 360 || bearingDeg % 22.5 != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Angle must be one of {0,22.5,45,...,337.5}"
            );
        }
        double normVal = bearingDeg % 360;
        int idx = (int) (normVal / 22.5) % Direction16.values().length;
        return Direction16.values()[idx];
    }

    // step from a point in a given direction
    public static LngLat stepFrom(LngLat start, Direction16 direction) {
        double rad = toRadians(direction.getBearingDeg());
        double dx = cos(rad) * STEP_SIZE;
        double dy = sin(rad) * STEP_SIZE;
        return new LngLat(start.lng() + dx, start.lat() + dy);
    }

    // start + raw angle
    public static LngLat nextPosition(LngLat start, double bearingDeg) {
        Direction16 dir = angleToDirection(bearingDeg);
        return stepFrom(start, dir);
    }
}
