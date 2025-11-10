package uk.ac.ed.acp.cw2.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URL;

@Configuration
@EnableScheduling
public class IlpRestServiceConfig {

    @Value("${ilp.service.url}")
    public String defaultServiceUrl;

    @Bean
    public String ilpEndPoint() {
        String envEndPoint = System.getenv("ILP_ENDPOINT");
        if (envEndPoint == null || envEndPoint.isEmpty()) {
            envEndPoint = defaultServiceUrl;
        }
        return envEndPoint;
    }

}
