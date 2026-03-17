package com.akashcodes.kafka.consumer;

import com.akashcodes.kafka.model.KafkaEvent;
import com.akashcodes.kafka.util.KafkaHeaderUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

/**
 * Production-grade generic Kafka consumer with retry and dead letter topic support.
 *
 * This base consumer demonstrates the recommended pattern. In your microservices,
 * extend or copy this pattern and implement your own business logic in the
 * processEvent() method or override it.
 *
 * Features:
 * - @RetryableTopic with exponential backoff
 * - Manual acknowledgement (prevents message loss)
 * - MDC context for correlated log tracing
 * - Micrometer metrics per topic and event type
 * - Structured logging at each stage
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenericKafkaConsumer {

    private final MeterRegistry meterRegistry;
    private final KafkaHeaderUtil kafkaHeaderUtil;

    /**
     * Main listener with automatic retry and DLT support.
     *
     * Retry strategy:
     *   Attempt 1 → main topic
     *   Attempt 2 → ${topic}-retry-1 (after 2s)
     *   Attempt 3 → ${topic}-retry-2 (after 4s)
     *   Attempt 4 → ${topic}-retry-3 (after 8s)
     *   Final failure → ${topic}-dlt
     *
     * The topic name is read from application properties via ${kafka.common.topic}.
     * Override in each microservice's application.yml.
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            dltTopicSuffix = "-dlt",
            retryTopicSuffix = "-retry",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false"  // Topics should be pre-created in production
    )
    @KafkaListener(
            topics = "${kafka.common.topic:default-topic}",
            groupId = "${kafka.common.consumer.group-id:global-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, KafkaEvent<?>> record,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        KafkaEvent<?> event = record.value();
        String correlationId = event != null ? event.getCorrelationId() : "unknown";
        String eventId = event != null ? event.getEventId() : "unknown";
        String eventType = event != null ? event.getEventType() : "unknown";

        // Set MDC context for correlated logging across the processing chain
        MDC.put("correlationId", correlationId);
        MDC.put("eventId", eventId);
        MDC.put("eventType", eventType);
        MDC.put("topic", topic);

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("[KAFKA-CONSUME] topic={} partition={} offset={} eventId={} eventType={} retryCount={}",
                    topic, partition, offset, eventId, eventType,
                    event != null ? event.getRetryCount() : 0);

            if (event == null) {
                log.warn("[KAFKA-CONSUME-NULL] Received null event at topic={} partition={} offset={}",
                        topic, partition, offset);
                acknowledgment.acknowledge();
                return;
            }

            // Delegate to processing logic
            processEvent(event, topic, partition, offset);

            // Commit offset only after successful processing
            acknowledgment.acknowledge();

            sample.stop(meterRegistry.timer("kafka.consumer.process",
                    "topic", topic,
                    "eventType", eventType,
                    "status", "success"));

            log.info("[KAFKA-CONSUME-SUCCESS] eventId={} eventType={} topic={}", eventId, eventType, topic);

        } catch (Exception ex) {
            sample.stop(meterRegistry.timer("kafka.consumer.process",
                    "topic", topic,
                    "eventType", eventType,
                    "status", "failure"));

            meterRegistry.counter("kafka.consumer.errors",
                    "topic", topic,
                    "eventType", eventType).increment();

            log.error("[KAFKA-CONSUME-ERROR] eventId={} eventType={} topic={} error={}",
                    eventId, eventType, topic, ex.getMessage(), ex);

            // Do NOT acknowledge — Spring Kafka will retry based on @RetryableTopic config
            // After all retries exhausted, message goes to DLT
            throw ex;

        } finally {
            MDC.clear();
        }
    }

    /**
     * Dead Letter Topic consumer — receives messages that exhausted all retries.
     *
     * In production, you should:
     * - Alert on DLT consumption (PagerDuty, Slack, etc.)
     * - Persist the failed message for manual inspection/replay
     * - Emit a metric/counter for DLT arrivals
     */
    @KafkaListener(
            topics = "${kafka.common.topic:default-topic}-dlt",
            groupId = "${kafka.common.consumer.group-id:global-consumer-group}-dlt",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeFromDlt(
            ConsumerRecord<String, KafkaEvent<?>> record,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        KafkaEvent<?> event = record.value();
        String eventId = event != null ? event.getEventId() : "unknown";
        String eventType = event != null ? event.getEventType() : "unknown";

        meterRegistry.counter("kafka.consumer.dlt.received",
                "topic", topic,
                "eventType", eventType).increment();

        log.error("[KAFKA-DLT] Dead letter received. topic={} eventId={} eventType={} payload={}",
                topic, eventId, eventType, event != null ? event.getPayload() : null);

        // TODO: Persist DLT message to database for manual replay
        // TODO: Send alert to operations team

        acknowledgment.acknowledge();
    }

    /**
     * Override this method in your microservice consumer to implement business logic.
     * The base implementation just logs the event payload.
     *
     * @param event     The deserialized KafkaEvent
     * @param topic     Topic from which the event was consumed
     * @param partition Partition number
     * @param offset    Partition offset
     */
    protected void processEvent(KafkaEvent<?> event, String topic, int partition, long offset) {
        log.info("[KAFKA-PROCESS] eventType={} payload={}", event.getEventType(), event.getPayload());
        // Subclasses should override this method with actual business logic
    }
}

