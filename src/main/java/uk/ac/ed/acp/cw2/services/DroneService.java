package uk.ac.ed.acp.cw2.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import uk.ac.ed.acp.cw2.data.DroneInfo;

import java.util.ArrayList;
import java.util.List;

@Service
public class DroneService {

    private final RestClient restClient;
    private final String ilpEndPoint;

    public DroneService(String ilpEndPoint) {
        this.restClient = RestClient.create();
        this.ilpEndPoint = ilpEndPoint;
    }

    public <T> List<T> fetch(String endpoint, ParameterizedTypeReference<List<T>> typeReference) {
        return restClient.get()
                .uri(ilpEndPoint + endpoint)
                .retrieve()
                .body(typeReference);
    }

    public List<DroneInfo> fetchDrones() {
        return fetch("/drones", new ParameterizedTypeReference<>() {});
    }

}
