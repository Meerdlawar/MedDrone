package uk.ac.ed.acp.cw2.data;

import lombok.Getter;

public class Directions {

    // Length of one move
    public static final double STEP_SIZE = 0.00015;

    @Getter
    public enum Direction16 {
        E(0), ENE(22.5), NE(45), NNE(67.5),
        N(90), NNW(112.5), NW(135), WNW(157.5),
        W(180), WSW(202.5), SW(225), SSW(247.5),
        S(270), SSE(292.5), SE(315), ESE(337.5);

        private final double bearingDeg;

        Direction16(double bearingDeg) {
            this.bearingDeg = bearingDeg;
        }

    }
}