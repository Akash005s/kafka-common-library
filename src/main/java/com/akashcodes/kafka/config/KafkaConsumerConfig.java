package com.akashcodes.kafka.config;

import com.akashcodes.kafka.handler.KafkaErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade Kafka Consumer configuration.
 *
 * Key production settings:
 * - Manual acknowledgement (MANUAL_IMMEDIATE): ensures offset is committed only after
 *   successful processing — prevents message loss on restart
 * - ErrorHandlingDeserializer: wraps JSON deserializer to handle poisoned messages
 *   gracefully instead of crashing the consumer
 * - Concurrency: multiple listener threads for throughput
 * - Observation enabled: integrates with Micrometer for metrics and tracing
 */
@Slf4j
@Configuration
@EnableKafka
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final KafkaErrorHandler kafkaErrorHandler;

    /**
     * Builds the core consumer configuration map.
     */
    private Map<String, Object> consumerConfigs() {
        KafkaProperties.Consumer props = kafkaProperties.getConsumer();
        Map<String, Object> config = new HashMap<>();

        // Connection
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, props.getGroupId());

        // Offset strategy
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.getAutoOffsetReset());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, props.isEnableAutoCommit());

        // Throughput tuning
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, props.getMaxPollRecords());
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, props.getMaxPollIntervalMs());

        // Session and heartbeat
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, props.getSessionTimeoutMs());
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, props.getHeartbeatIntervalMs());

        // Use ErrorHandlingDeserializer to gracefully handle poisoned messages
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // Trust specific packages for deserialization (security best practice)
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        // Apply security config if needed
        applySecurity(config);

        log.info("Kafka Consumer configured with bootstrapServers={}, groupId={}, concurrency={}",
                props.getBootstrapServers(), props.getGroupId(), props.getConcurrency());

        return config;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    /**
     * Primary listener container factory.
     * - Manual offset acknowledgement for at-least-once delivery semantics
     * - Custom error handler for retry / DLT routing
     * - Observation enabled for Micrometer metrics
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Manual acknowledgement: offset only committed after processing succeeds
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Number of concurrent consumer threads
        factory.setConcurrency(kafkaProperties.getConsumer().getConcurrency());

        // Plug in custom error handler (handles retries and DLT routing)
        factory.setCommonErrorHandler(kafkaErrorHandler.defaultErrorHandler());

        // Enable Micrometer observation (metrics + distributed tracing)
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
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

            log.info("Kafka Consumer security protocol: {}", security.getProtocol());
        }
    }
}

