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

    public DroneService(String ilpEndPoint) {
        this.restClient = RestClient.builder()
                .baseUrl(ilpEndPoint)
                .build();
    }

    public <type> List<type> fetch(ParameterizedTypeReference<List<type>> typeRef, String path) {

            return restClient
                    .get()
                    .uri(path)
                    .retrieve()
                    .body(typeRef);
    }

    public List<DroneInfo> fetchDrones() {
        return fetch(new ParameterizedTypeReference<>() {}, "/drones");
    }
}
