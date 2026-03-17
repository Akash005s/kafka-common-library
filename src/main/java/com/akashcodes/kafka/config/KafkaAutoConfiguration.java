package com.akashcodes.kafka.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.akashcodes.kafka.retry.RetryConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(KafkaProperties.class)
@ComponentScan(basePackages = "com.akashcodes.kafka")   // ← scans all @Service/@Component in the library
@Import({
        KafkaProducerConfig.class,
        KafkaConsumerConfig.class,
        RetryConfiguration.class
})
public class KafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}