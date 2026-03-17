package com.akashcodes.kafka.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Centralized Kafka error handling configuration.
 *
 * Responsibilities:
 * - Exponential backoff for transient errors (DB timeouts, API failures)
 * - Dead Letter Topic publishing for messages that exhaust retries
 * - Non-retryable exception classification (e.g. deserialization errors)
 * - Structured error logging
 *
 * Non-retryable exceptions go directly to the DLT without retrying:
 * - DeserializationException: malformed/poisoned messages should not be retried
 * - IllegalArgumentException: bad data that will always fail
 * - NullPointerException: programming errors that retries will not fix
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaErrorHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Builds the DefaultErrorHandler with:
     * - Exponential backoff: 1s → 2s → 4s → 8s → 10s (cap)
     * - DLT publishing for unrecoverable messages
     * - Non-retryable exception list
     */
    @Bean
    public CommonErrorHandler defaultErrorHandler() {

        // DLT recoverer: publishes failed records to <original-topic>-dlt
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    String dltTopic = record.topic() + "-dlt";
                    log.error("[KAFKA-DLT-PUBLISH] Sending to DLT. topic={} dltTopic={} key={} error={}",
                            record.topic(), dltTopic, record.key(), ex.getMessage(), ex);
                    return new org.apache.kafka.common.TopicPartition(dltTopic, record.partition());
                });

        // Exponential backoff: starts at 1000ms, doubles each time, caps at 10000ms
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(30000L); // Stop retrying after 30 seconds total

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // These exceptions will NOT be retried — they go directly to DLT
        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,  // Poisoned/malformed messages
                IllegalArgumentException.class,  // Bad data that always fails
                IllegalStateException.class,     // Unexpected state issues
                NullPointerException.class       // Programming errors
        );

        // Log the full record details when an error occurs
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[KAFKA-RETRY] attempt={} topic={} partition={} offset={} key={} error={}",
                        deliveryAttempt,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        ex.getMessage())
        );

        return errorHandler;
    }
}

