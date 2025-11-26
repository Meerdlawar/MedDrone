package uk.ac.ed.acp.cw2.configuration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OPTIMIZED: Cache configuration for static data
 * Enables caching for drones, service points, availability, and restricted areas
 * This significantly reduces API calls to the external REST service
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "drones",
                "droneAvailability",
                "servicePoints",
                "restrictedAreas"
        );
    }
}
