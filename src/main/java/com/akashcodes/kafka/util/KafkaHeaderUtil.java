package com.akashcodes.kafka.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility for reading and writing Kafka message headers.
 *
 * Headers are used for cross-cutting concerns:
 * - Distributed tracing (trace ID, span ID, correlation ID)
 * - Event metadata (source service, schema version)
 * - Routing (target service hint)
 * - Security tokens (not recommended for sensitive data)
 *
 * All header values are UTF-8 encoded byte arrays.
 */
@Slf4j
@Component
public class KafkaHeaderUtil {

    // Standard header key constants — use these across all services for consistency
    public static final String HEADER_CORRELATION_ID  = "X-Correlation-Id";
    public static final String HEADER_TRACE_ID        = "X-Trace-Id";
    public static final String HEADER_SPAN_ID         = "X-Span-Id";
    public static final String HEADER_SOURCE_SERVICE  = "X-Source-Service";
    public static final String HEADER_EVENT_TYPE      = "X-Event-Type";
    public static final String HEADER_SCHEMA_VERSION  = "X-Schema-Version";
    public static final String HEADER_RETRY_COUNT     = "X-Retry-Count";
    public static final String HEADER_TIMESTAMP       = "X-Timestamp";

    /**
     * Reads a header value from a ConsumerRecord as a String.
     *
     * @return Optional containing the header value, or empty if not present
     */
    public Optional<String> getHeader(ConsumerRecord<?, ?> record, String headerKey) {
        Header header = record.headers().lastHeader(headerKey);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }

    /**
     * Reads all headers from a ConsumerRecord into a String map.
     */
    public Map<String, String> getAllHeaders(ConsumerRecord<?, ?> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            if (header.value() != null) {
                headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        return headers;
    }

    /**
     * Adds a string header to a ProducerRecord's headers.
     */
    public void addHeader(ProducerRecord<?, ?> record, String key, String value) {
        if (value != null) {
            record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Adds a string header to a Headers object (used in producer interceptors).
     */
    public void addHeader(Headers headers, String key, String value) {
        if (value != null) {
            headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Removes an existing header by key and sets a new value.
     * Use when modifying headers in an interceptor chain.
     */
    public void setHeader(Headers headers, String key, String value) {
        headers.remove(key);
        if (value != null) {
            headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Extracts the correlation ID from a ConsumerRecord header.
     * Returns a default value if not present.
     */
    public String extractCorrelationId(ConsumerRecord<?, ?> record) {
        return getHeader(record, HEADER_CORRELATION_ID).orElse("unknown");
    }

    /**
     * Extracts the source service name from a ConsumerRecord header.
     */
    public String extractSourceService(ConsumerRecord<?, ?> record) {
        return getHeader(record, HEADER_SOURCE_SERVICE).orElse("unknown");
    }

    /**
     * Logs all headers from a consumer record at DEBUG level.
     * Useful during development and troubleshooting.
     */
    public void logHeaders(ConsumerRecord<?, ?> record) {
        if (log.isDebugEnabled()) {
            Map<String, String> headers = getAllHeaders(record);
            log.debug("[KAFKA-HEADERS] topic={} partition={} offset={} headers={}",
                    record.topic(), record.partition(), record.offset(), headers);
        }
    }
}

