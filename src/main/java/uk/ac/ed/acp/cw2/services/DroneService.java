package uk.ac.ed.acp.cw2.services;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import uk.ac.ed.acp.cw2.data.DroneInfo;
import uk.ac.ed.acp.cw2.data.QueryAttributes;

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

        switch (attr) {
            case "cooling":
                return compareBoolean(cap.cooling(), op, val);
            case "heating":
                return compareBoolean(cap.heating(), op, val);
            case "capacity":
                return compareDouble(cap.capacity(), op, val);
            case "maxMoves":
                return compareDouble(cap.maxMoves(), op, val);
            case "costPerMove":
                return compareDouble(cap.costPerMove(), op, val);
            case "costInitial":
                return compareDouble(cap.costInitial(), op, val);
            case "costFinal":
                return compareDouble(cap.costFinal(), op, val);
            default:
                throw new IllegalArgumentException("Unknown attribute: " + attr);
        }
    }

    private boolean compareBoolean(boolean fieldValue, String operator, String value) {
        boolean v = Boolean.parseBoolean(value);
        switch (operator) {
            case "=":  return fieldValue == v;
            case "!=": return fieldValue != v;
            default:   throw new IllegalArgumentException(
                    "Operator '" + operator + "' not supported for boolean");
        }
    }

    private boolean compareDouble(double fieldValue, String operator, String value) {
        double v = Double.parseDouble(value);
        switch (operator) {
            case "=":  return fieldValue == v;
            case "!=": return fieldValue != v;
            case "<":  return fieldValue <  v;
            case ">":  return fieldValue >  v;
            default:   throw new IllegalArgumentException(
                    "Operator '" + operator + "' not supported for numeric");
        }
    }
}