package uk.ac.ed.acp.cw2.configuration;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class IlpRestServiceConfig {

    private static final String defaultUrl =
            "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net";

    @Bean
    public String endPoint(@Value("${ILP_ENDPOINT:" + defaultUrl + "}") String url) {
        return url;
    }
}

