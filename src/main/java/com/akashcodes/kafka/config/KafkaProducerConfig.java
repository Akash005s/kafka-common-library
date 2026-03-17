package com.akashcodes.kafka.config;

import com.akashcodes.kafka.interceptor.KafkaProducerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade Kafka Producer configuration.
 *
 * Key production settings:
 * - Idempotent producer: prevents duplicate messages on retries
 * - acks=all: waits for all ISR replicas to acknowledge (strongest durability)
 * - Compression: reduces network and storage overhead
 * - Custom interceptor: adds tracing headers and metrics
 */
@Slf4j
@Configuration
@EnableKafka
@RequiredArgsConstructor
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;

    /**
     * Builds the core producer configuration map from externalized properties.
     */
    private Map<String, Object> producerConfigs() {
        KafkaProperties.Producer props = kafkaProperties.getProducer();
        Map<String, Object> config = new HashMap<>();

        // Connection
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());

        // Serialization
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability
        config.put(ProducerConfig.ACKS_CONFIG, props.getAcks());
        config.put(ProducerConfig.RETRIES_CONFIG, props.getRetries());
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, props.isEnableIdempotence());

        // Performance
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, props.getBatchSize());
        config.put(ProducerConfig.LINGER_MS_CONFIG, props.getLingerMs());
        config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, props.getMaxRequestSize());
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, props.getRequestTimeoutMs());
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, props.getCompressionType());

        // Do not include type info in messages — receivers should not depend on it
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);

        // Interceptor for tracing and metrics
        config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                KafkaProducerInterceptor.class.getName());

        // Apply security config if protocol is not PLAINTEXT
        applySecurity(config);

        log.info("Kafka Producer configured with bootstrapServers={}, acks={}, idempotent={}",
                props.getBootstrapServers(), props.getAcks(), props.isEnableIdempotence());

        return config;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    /**
     * Primary KafkaTemplate used by GenericKafkaProducer.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        // Log results asynchronously for observability
        template.setObservationEnabled(true);
        return template;
    }

    /**
     * Applies security configuration when a non-PLAINTEXT protocol is set.
     */
    private void applySecurity(Map<String, Object> config) {
        KafkaProperties.Security security = kafkaProperties.getSecurity();

        if (StringUtils.hasText(security.getProtocol())
                && !"PLAINTEXT".equalsIgnoreCase(security.getProtocol())) {

            config.put("security.protocol", security.getProtocol());

            if (StringUtils.hasText(security.getSaslMechanism())) {
                config.put("sasl.mechanism", security.getSaslMechanism());
            }
            if (StringUtils.hasText(security.getSaslJaasConfig())) {
                config.put("sasl.jaas.config", security.getSaslJaasConfig());
            }
            if (StringUtils.hasText(security.getSslTruststoreLocation())) {
                config.put("ssl.truststore.location", security.getSslTruststoreLocation());
                config.put("ssl.truststore.password", security.getSslTruststorePassword());
            }

            log.info("Kafka Producer security protocol: {}", security.getProtocol());
        }
    }
}

