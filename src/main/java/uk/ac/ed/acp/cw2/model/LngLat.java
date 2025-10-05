package uk.ac.ed.acp.cw2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LngLat {
    private final Double lng;
    private final Double lat;
//    private final int angle;

    @JsonCreator
    public LngLat(@JsonProperty("lng") Double lng, @JsonProperty("lat") Double lat) {
        if (lng == null) {
            throw new IllegalArgumentException("lng is required");
        }
        if (lat == null) {
            throw new IllegalArgumentException("lat is required");
        }

        if (!Double.isFinite(lng)) {
            throw new IllegalArgumentException("lng must be finite");
        }
        if (!Double.isFinite(lat)) {
            throw new IllegalArgumentException("lat must be finite");
        }

        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lng must be between -180 and 180");
        }
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be between -90 and 90");
        }

        this.lng = lng;
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }
    public Double getLat() {
        return lat;
    }

}
