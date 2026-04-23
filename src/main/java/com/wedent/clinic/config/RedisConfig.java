package com.wedent.clinic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis wiring.  Redis is a first-class dependency of the app because refresh
 * tokens and the access-token blacklist live there; there is no in-memory
 * fallback for those features.  Unit tests mock the service interfaces; the
 * integration tests spin up a testcontainer redis.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class RedisConfig {

    /**
     * String-only template.  The blacklist and rate-limiter write short opaque
     * strings and need predictable UTF-8 keys — not Java-serialized blobs.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    /**
     * Spring Cache abstraction with Jackson JSON values + a default TTL.
     * JSR-310 module is registered so {@code LocalDate}/{@code Instant} fields
     * survive round-trips — a common landmine with the stock serializer.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf, CacheProperties props) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.defaultTtl())
                .prefixCacheNameWith(props.keyPrefix() + "cache:")
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(defaults)
                .transactionAware() // only write to cache after the outer @Transactional commits
                .build();
    }
}
