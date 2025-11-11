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
    private final RestClient client;

    public DroneService(String ilpEndPoint) {
        this.client = RestClient.builder()
                .baseUrl(ilpEndPoint)
                .build();
    }

    public <T> List<T> fetch(String endpoint, ParameterizedTypeReference<List<T>> typeRef) {
        try {
            List<T> body = client.get().uri(endpoint).retrieve().body(typeRef);
            return body != null ? body : List.of();
        } catch (Exception ex) {
            // log and fail soft; or rethrow as a custom RuntimeException
            // logger.error("Failed to fetch {}: {}", endpoint, ex.getMessage(), ex);
            return List.of();
        }
    }

    public List<DroneInfo> fetchDrones() {
        return fetch("/drones", new ParameterizedTypeReference<>() {});
    }
}
