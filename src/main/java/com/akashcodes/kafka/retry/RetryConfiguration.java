package com.akashcodes.kafka.retry;

import com.akashcodes.kafka.config.KafkaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;

/**
 * Programmatic retry topic configuration for the Kafka library.
 *
 * This complements the @RetryableTopic annotation approach on consumers.
 * Use this bean for global defaults, and @RetryableTopic per-listener for
 * topic-specific overrides.
 *
 * Retry topic naming convention:
 *   original-topic-retry-1
 *   original-topic-retry-2
 *   original-topic-retry-3
 *   original-topic-dlt
 *
 * Topics must be pre-created in production with appropriate replication and
 * retention settings. Use autoCreateTopics = false to enforce this.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RetryConfiguration {

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Global retry topic configuration applied to all @KafkaListener methods
     * that do NOT have their own @RetryableTopic annotation.
     *
     * Adjust attempts and backoff per your SLA requirements.
     */
    @Bean
    public RetryTopicConfiguration globalRetryTopicConfig() {
        KafkaProperties.Retry retryProps = kafkaProperties.getRetry();

        log.info("Configuring global retry: maxAttempts={} initialInterval={}ms multiplier={} maxInterval={}ms",
                retryProps.getMaxAttempts(),
                retryProps.getInitialIntervalMs(),
                retryProps.getMultiplier(),
                retryProps.getMaxIntervalMs());

        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(retryProps.getMaxAttempts())
                .exponentialBackoff(
                        retryProps.getInitialIntervalMs(),
                        retryProps.getMultiplier(),
                        retryProps.getMaxIntervalMs()
                )
                .retryTopicSuffix(retryProps.getRetrySuffix())
                .dltSuffix(retryProps.getDltSuffix())
                // Topics must be pre-created; do not auto-create in production
                .doNotAutoCreateRetryTopics()
                // Retry topics use the same consumer group as main topic
                .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.SINGLE_TOPIC)
                .create(kafkaTemplate);
    }
}

