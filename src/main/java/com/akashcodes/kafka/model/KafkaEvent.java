package com.akashcodes.kafka.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Standardized Kafka event wrapper used across all microservices.
 * Provides consistent structure for event metadata and payload.
 *
 * @param <T> The type of the event payload
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaEvent<T> {

    /**
     * Unique identifier for each event instance (auto-generated if not set).
     */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /**
     * Logical type of the event, e.g., "ORDER_CREATED", "PAYMENT_PROCESSED".
     */
    private String eventType;

    /**
     * Name of the microservice that produced this event.
     */
    private String source;

    /**
     * Schema version of the event for forward/backward compatibility.
     */
    @Builder.Default
    private String schemaVersion = "1.0";

    /**
     * Correlation ID for distributed tracing across services.
     */
    private String correlationId;

    /**
     * The actual business payload.
     */
    private T payload;

    /**
     * Timestamp when this event was created.
     */
    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Number of times this event has been retried (used for retry tracking).
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * Optional metadata headers (custom key-value pairs for additional context).
     */
    private Map<String, String> metadata;

    /**
     * Convenience factory method to create a new event with required fields.
     */
    public static <T> KafkaEvent<T> of(String eventType, String source, T payload) {
        return KafkaEvent.<T>builder()
                .eventType(eventType)
                .source(source)
                .payload(payload)
                .build();
    }

    /**
     * Convenience factory method with correlation ID for distributed tracing.
     */
    public static <T> KafkaEvent<T> of(String eventType, String source, T payload, String correlationId) {
        return KafkaEvent.<T>builder()
                .eventType(eventType)
                .source(source)
                .payload(payload)
                .correlationId(correlationId)
                .build();
    }
}

