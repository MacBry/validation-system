package com.mac.bry.validationsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis caching and rate limiting.
 *
 * <p>
 * This class defines the RedisTemplate used for caching PDF documents
 * in {@link com.mac.bry.validationsystem.wizard.pdf.ValidationPlanPdfService}.
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * Defines a RedisTemplate for storing PDF bytes with String keys.
     * Required for ValidationPlanPdfService.
     */
    @Bean
    public RedisTemplate<String, byte[]> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use Byte array serializer for values (raw storage)
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        
        template.afterPropertiesSet();
        return template;
    }
}
