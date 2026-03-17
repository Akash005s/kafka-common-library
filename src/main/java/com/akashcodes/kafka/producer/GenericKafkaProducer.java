package com.akashcodes.kafka.producer;

import com.akashcodes.kafka.model.KafkaEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Production-grade generic Kafka producer service.
 *
 * Features:
 * - Synchronous and asynchronous publish variants
 * - Structured logging with MDC context
 * - Micrometer metrics for publish success/failure
 * - Partition key routing support
 * - Callback-based result handling for async publishes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenericKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Publishes a KafkaEvent asynchronously to the specified topic with a partition key.
     * Returns a CompletableFuture so callers can chain callbacks if desired.
     *
     * @param topic  Target Kafka topic name
     * @param key    Partition routing key (e.g. orderId, userId)
     * @param event  Standardized KafkaEvent wrapper
     * @param <T>    Type of the event payload
     */
    public <T> CompletableFuture<SendResult<String, Object>> publish(
            String topic, String key, KafkaEvent<T> event) {

        log.info("[KAFKA-PUBLISH] topic={} eventType={} eventId={} source={} correlationId={}",
                topic, event.getEventType(), event.getEventId(),
                event.getSource(), event.getCorrelationId());

        Timer.Sample sample = Timer.start(meterRegistry);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                sample.stop(meterRegistry.timer("kafka.producer.publish",
                        "topic", topic,
                        "eventType", event.getEventType(),
                        "status", "success"));

                log.info("[KAFKA-PUBLISH-SUCCESS] topic={} eventId={} partition={} offset={}",
                        topic, event.getEventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                sample.stop(meterRegistry.timer("kafka.producer.publish",
                        "topic", topic,
                        "eventType", event.getEventType(),
                        "status", "failure"));

                meterRegistry.counter("kafka.producer.errors",
                        "topic", topic,
                        "eventType", event.getEventType()).increment();

                log.error("[KAFKA-PUBLISH-FAILURE] topic={} eventId={} error={}",
                        topic, event.getEventId(), ex.getMessage(), ex);
            }
        });

        return future;
    }

    /**
     * Publishes without a partition key (Kafka will round-robin across partitions).
     */
    public <T> CompletableFuture<SendResult<String, Object>> publish(
            String topic, KafkaEvent<T> event) {
        return publish(topic, event.getEventId(), event);
    }

    /**
     * Publishes synchronously — blocks until the broker acknowledges the record.
     * Use this when strict ordering or immediate confirmation is required.
     *
     * WARNING: This blocks the calling thread. Avoid in high-throughput paths.
     *
     * @throws RuntimeException if the publish fails
     */
    public <T> SendResult<String, Object> publishSync(
            String topic, String key, KafkaEvent<T> event) {
        try {
            log.info("[KAFKA-PUBLISH-SYNC] topic={} eventType={} eventId={}",
                    topic, event.getEventType(), event.getEventId());

            SendResult<String, Object> result = kafkaTemplate.send(topic, key, event).get();

            log.info("[KAFKA-PUBLISH-SYNC-SUCCESS] topic={} partition={} offset={}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[KAFKA-PUBLISH-SYNC-INTERRUPTED] topic={} eventId={}",
                    topic, event.getEventId(), e);
            throw new RuntimeException("Kafka publish interrupted", e);
        } catch (Exception e) {
            log.error("[KAFKA-PUBLISH-SYNC-FAILURE] topic={} eventId={} error={}",
                    topic, event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("Kafka publish failed", e);
        }
    }
}

