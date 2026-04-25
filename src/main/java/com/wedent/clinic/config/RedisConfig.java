package com.wedent.clinic.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@EnableCaching
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class RedisConfig {

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedisTemplate<?, ?> redisTemplate() {
        RedisTemplate<byte[], byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public RedissonConnectionFactory redisConnectionFactory() {
        return new RedissonConnectionFactory(redissonClient());
    }

    @Bean
    public CacheManager cacheManager() {
        return RedisCacheManager.builder(redisConnectionFactory())
                .withInitialCacheConfigurations(constructInitialCacheConfigurations())
                .transactionAware()
                .build();
    }

    @Bean
    public RedissonClient redissonClient() {
        String url = redisUrl;
        if (!url.startsWith("redis://") && !url.startsWith("rediss://")) {
            url = "redis://" + url;
        }

        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;

        Config config = new Config();
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress(scheme + "://" + host + ":" + port);

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int colonIdx = userInfo.indexOf(':');
            if (colonIdx > 0) {
                String username = userInfo.substring(0, colonIdx);
                String password = userInfo.substring(colonIdx + 1);
                if (!"default".equals(username)) {
                    singleServer.setUsername(username);
                }
                singleServer.setPassword(password);
            } else {
                singleServer.setPassword(userInfo);
            }
        }

        return Redisson.create(config);
    }

    private Map<String, RedisCacheConfiguration> constructInitialCacheConfigurations() {
        Map<String, RedisCacheConfiguration> map = new HashMap<>();
        map.put("jwtToken", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(1)));
        return map;
    }
}
