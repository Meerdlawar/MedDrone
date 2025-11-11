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

    private static final String DEFAULT_URL =
            "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net";

    @Bean
    public String ilpEndPoint(@Value("${ILP_SERVICE_URL:" + DEFAULT_URL + "}") String configuredUrl) {
        return configuredUrl;
    }
}

