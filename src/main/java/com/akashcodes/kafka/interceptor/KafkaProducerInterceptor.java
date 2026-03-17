package com.akashcodes.kafka.interceptor;

import com.akashcodes.kafka.util.KafkaHeaderUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Producer interceptor that enriches every outgoing message with:
 * - Correlation ID (from MDC context if available, else new UUID)
 * - Trace ID and Span ID (from MDC/OpenTelemetry context)
 * - Source service identifier (from environment variable or property)
 * - Publish timestamp
 *
 * This interceptor is registered via ProducerConfig.INTERCEPTOR_CLASSES_CONFIG
 * in KafkaProducerConfig and runs transparently before every message is sent.
 *
 * Note: This class is instantiated by Kafka internals (not Spring),
 * so Spring @Autowired injection does NOT work here.
 * Use MDC and system properties for context propagation instead.
 */
@Slf4j
public class KafkaProducerInterceptor implements ProducerInterceptor<String, Object> {

    private String sourceServiceName;

    @Override
    public void configure(Map<String, ?> configs) {
        // Read source service name from producer config or fall back to env variable
        Object serviceName = configs.get("spring.application.name");
        this.sourceServiceName = serviceName != null
                ? serviceName.toString()
                : System.getenv().getOrDefault("SPRING_APPLICATION_NAME", "unknown-service");

        log.info("KafkaProducerInterceptor initialized for service: {}", sourceServiceName);
    }

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        try {
            // Correlation ID: propagate from MDC (set by incoming HTTP request filter)
            // or generate a new one for producer-originated events
            String correlationId = MDC.get("correlationId");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            // Trace and span IDs from OpenTelemetry/Micrometer Tracing (via MDC bridge)
            String traceId = MDC.get("traceId");
            String spanId  = MDC.get("spanId");

            // Enrich headers
            record.headers().add(KafkaHeaderUtil.HEADER_CORRELATION_ID,
                    correlationId.getBytes());
            record.headers().add(KafkaHeaderUtil.HEADER_SOURCE_SERVICE,
                    sourceServiceName.getBytes());
            record.headers().add(KafkaHeaderUtil.HEADER_TIMESTAMP,
                    Instant.now().toString().getBytes());

            if (traceId != null && !traceId.isBlank()) {
                record.headers().add(KafkaHeaderUtil.HEADER_TRACE_ID, traceId.getBytes());
            }
            if (spanId != null && !spanId.isBlank()) {
                record.headers().add(KafkaHeaderUtil.HEADER_SPAN_ID, spanId.getBytes());
            }

            log.debug("[INTERCEPTOR-ENRICH] topic={} key={} correlationId={} traceId={}",
                    record.topic(), record.key(), correlationId, traceId);

        } catch (Exception ex) {
            // Never let an interceptor crash the producer
            log.warn("[INTERCEPTOR-ERROR] Failed to enrich headers for topic={}: {}",
                    record.topic(), ex.getMessage());
        }

        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (exception != null) {
            log.error("[INTERCEPTOR-ACK-ERROR] topic={} partition={} error={}",
                    metadata != null ? metadata.topic() : "unknown",
                    metadata != null ? metadata.partition() : -1,
                    exception.getMessage());
        }
    }

    @Override
    public void close() {
        log.info("KafkaProducerInterceptor closed for service: {}", sourceServiceName);
    }
}

