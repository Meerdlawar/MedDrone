package uk.ac.ed.acp.cw2.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LngLat {
    private final Double lng;
    private final Double lat;

    public LngLat(Double lng, Double lat) {
        if (lng == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Longitude is required");
        }
        if (lat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Latitude is required");
        }

        if (lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lng must be between -180 and 180");
        }
        if (lat < -90 || lat > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lng must be between -90 and 90");

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
