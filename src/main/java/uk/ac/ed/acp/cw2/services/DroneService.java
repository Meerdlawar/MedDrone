package uk.ac.ed.acp.cw2.services;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import uk.ac.ed.acp.cw2.data.DroneInfo;

import java.util.ArrayList;
import java.util.List;

import static jakarta.xml.bind.DatatypeConverter.parseBoolean;
import static jakarta.xml.bind.DatatypeConverter.parseDouble;

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

    public int[] queryAttribute(String attribute, String operator, String value) {
        List<DroneInfo> drones = fetchDrones();
        List<Integer> droneIds = new ArrayList<>();

        for (DroneInfo drone : drones) {
            var cap = drone.capability();
            boolean matches;

            switch (attribute) {
                case "cooling":
                    matches = compareBoolean(cap.cooling(), operator, value);
                    break;
                case "heating":
                    matches = compareBoolean(cap.heating(), operator, value);
                    break;
                case "capacity":
                    matches = compareDouble(cap.capacity(), operator, value);
                    break;
                case "maxMoves":
                    matches = compareDouble(cap.maxMoves(), operator, value);
                    break;
                case "costPerMove":
                    matches = compareDouble(cap.costPerMove(), operator, value);
                    break;
                case "costInitial":
                    matches = compareDouble(cap.costInitial(), operator, value);
                    break;
                case "costFinal":
                    matches = compareDouble(cap.costFinal(), operator, value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown attribute: " + attribute);
            }

            if (matches) {
                droneIds.add(drone.id());
            }
        }

        return droneIds.stream().mapToInt(i -> i).toArray();
    }

    private boolean compareBoolean(boolean fieldValue, String operator, String value) {
        boolean v = parseBoolean(value);
        switch (operator) {
            case "=":  return fieldValue == v;
            case "!=": return fieldValue != v;
            default:   throw new IllegalArgumentException("Unknown boolean operator: " + operator);
        }
    }

    private boolean compareDouble(double fieldValue, String operator, String value) {
        double v = parseDouble(value);
        switch (operator) {
            case "=":  return fieldValue == v;
            case "!=": return fieldValue != v;
            case "<":  return fieldValue < v;
            case ">":  return fieldValue > v;
            default:   throw new IllegalArgumentException("Unknown numeric operator: " + operator);
        }
    }

    private boolean parseBoolean(String s) {
        return Boolean.parseBoolean(s);
    }

    private double parseDouble(String s) {
        return Double.parseDouble(s);
    }

}
