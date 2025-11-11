package uk.ac.ed.acp.cw2.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.URL;

@Configuration
@EnableScheduling
public class IlpRestServiceConfig {

    private static final String defaultServiceUrl = "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net";

    @Bean
    public String ilpEndPoint() {
        String envEndPoint = System.getenv("${ILP_SERVICE_URL}");
        if (envEndPoint == null || envEndPoint.isEmpty()) {
            envEndPoint = defaultServiceUrl;
        }
        return envEndPoint;
    }


}
