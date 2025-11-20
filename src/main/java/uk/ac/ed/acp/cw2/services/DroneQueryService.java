package uk.ac.ed.acp.cw2.services;


import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import uk.ac.ed.acp.cw2.dto.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DroneQueryService {
    private final RestClient restClient;

    public DroneQueryService(String ilpEndPoint) {
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

    public List<DronesForServicePoints> fetchDroneAvailability() {
        return fetch(new ParameterizedTypeReference<>() {}, "/drones-for-service-points");
    }

    public List<ServicePoints> fetchServicePoints() {
        return fetch(new ParameterizedTypeReference<>() {}, "/service-points");
    }

    public List<RestrictedAreas> fetchRestrictedAreas() {
        return fetch(new ParameterizedTypeReference<>() {}, "/restricted-areas");
    }

//    public List<DronesForServicePoints> fetchDroneAvailability() {
//        return fetch(new ParameterizedTypeReference<>() {}, "/drones-for-service-points");
//    }

    public int[] filterDroneAttributes(List<QueryAttributes> reqs) {
        List<DroneInfo> drones = fetchDrones();
        List<Integer> out = new ArrayList<>();

        for (DroneInfo d : drones) {
            if (matchesAll(d, reqs)) {
                out.add(d.id());
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean matchesAll(DroneInfo d, List<QueryAttributes> reqs) {
        for (QueryAttributes r : reqs) {
            if (!matches(d, r)) return false;
        }
        return true;
    }

    private boolean matches(DroneInfo d, QueryAttributes r) {
        var cap = d.capability();
        String attr = r.attribute();
        String op   = r.operator();
        String val  = r.value();

        return switch (attr) {
            case "cooling" -> compareBoolean(cap.cooling(), op, val);
            case "heating" -> compareBoolean(cap.heating(), op, val);
            case "capacity" -> compareDouble(cap.capacity(), op, val);
            case "maxMoves" -> compareDouble(cap.maxMoves(), op, val);
            case "costPerMove" -> compareDouble(cap.costPerMove(), op, val);
            case "costInitial" -> compareDouble(cap.costInitial(), op, val);
            case "costFinal" -> compareDouble(cap.costFinal(), op, val);
            default -> throw new IllegalArgumentException("Unknown attribute: " + attr);
        };
    }

    private boolean compareBoolean(boolean fieldValue, String operator, String value) {
        boolean v = Boolean.parseBoolean(value);
        return switch (operator) {
            case "=" -> fieldValue == v;
            case "!=" -> fieldValue != v;
            default -> throw new IllegalArgumentException(
                    "Operator '" + operator + "' not supported for boolean");
        };
    }

    private boolean compareDouble(double fieldValue, String operator, String value) {
        double v = Double.parseDouble(value);
        return switch (operator) {
            case "=" -> fieldValue == v;
            case "!=" -> fieldValue != v;
            case "<" -> fieldValue < v;
            case ">" -> fieldValue > v;
            default -> throw new IllegalArgumentException(
                    "Operator '" + operator + "' not supported for numeric");
        };
    }

    public Map<Integer, LngLat> fetchDroneOriginLocations() {
        List<ServicePoints> servicePoints = fetchServicePoints();
        List<DronesForServicePoints> dronesForServicePoints = fetchDroneAvailability();

        // map servicePointId -> LngLat
        Map<Integer, LngLat> servicePointLocations = servicePoints.stream()
                .collect(Collectors.toMap(ServicePoints::id, ServicePoints::location));

        Map<Integer, LngLat> droneOriginMap = new HashMap<>();

        for (DronesForServicePoints sp : dronesForServicePoints) {
            LngLat location = servicePointLocations.get(sp.servicePointId());
            if (location == null) {
                // Inconsistent data – maybe log, but don't crash
                continue;
            }
            for (ListDrones drone : sp.drones()) {
                droneOriginMap.put(drone.id(), location);
            }
        }

        return droneOriginMap;
    }

    public Optional<LngLat> fetchDroneOrigin(int droneId) {
        Map<Integer, LngLat> origins = fetchDroneOriginLocations();
        return Optional.ofNullable(origins.get(droneId));
    }


}