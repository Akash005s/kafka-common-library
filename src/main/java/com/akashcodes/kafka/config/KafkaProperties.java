package com.akashcodes.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration properties for the Kafka library.
 * All properties are prefixed with "kafka.common" in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "kafka.common")
public class KafkaProperties {

    private Producer producer = new Producer();
    private Consumer consumer = new Consumer();
    private Retry retry = new Retry();
    private Security security = new Security();

    @Data
    public static class Producer {
        /** Comma-separated Kafka bootstrap servers. */
        private String bootstrapServers = "localhost:9092";

        /** Number of acknowledgments required. "all" = strongest guarantee. */
        private String acks = "all";

        /** Number of retries on transient errors. */
        private int retries = 3;

        /** Batch size in bytes for grouping records. */
        private int batchSize = 16384;

        /** Linger time in milliseconds before sending a batch. */
        private int lingerMs = 1;

        /** Max size for a single request in bytes (1MB default). */
        private int maxRequestSize = 1048576;

        /** Request timeout in milliseconds. */
        private int requestTimeoutMs = 30000;

        /** Enable idempotent producer (prevents duplicate messages). */
        private boolean enableIdempotence = true;

        /** Compression type: none, gzip, snappy, lz4, zstd. */
        private String compressionType = "snappy";
    }

    @Data
    public static class Consumer {
        /** Comma-separated Kafka bootstrap servers. */
        private String bootstrapServers = "localhost:9092";

        /** Consumer group ID. */
        private String groupId = "global-consumer-group";

        /** Auto offset reset strategy: earliest or latest. */
        private String autoOffsetReset = "earliest";

        /** Disable auto-commit; we commit manually for reliability. */
        private boolean enableAutoCommit = false;

        /** Max number of records returned per poll. */
        private int maxPollRecords = 100;

        /** Session timeout for the consumer in milliseconds. */
        private int sessionTimeoutMs = 30000;

        /** Heartbeat interval in milliseconds. */
        private int heartbeatIntervalMs = 10000;

        /** Max time between polls before consumer is considered dead. */
        private int maxPollIntervalMs = 300000;

        /** Number of concurrent consumer threads. */
        private int concurrency = 3;

        /** Trusted packages for JSON deserialization. */
        private String trustedPackages = "*";
    }

    @Data
    public static class Retry {
        /** Max number of retry attempts. */
        private int maxAttempts = 3;

        /** Initial backoff delay in milliseconds. */
        private long initialIntervalMs = 1000;

        /** Multiplier for exponential backoff. */
        private double multiplier = 2.0;

        /** Maximum backoff interval in milliseconds. */
        private long maxIntervalMs = 10000;

        /** Suffix appended to topic name for dead letter topic. */
        private String dltSuffix = "-dlt";

        /** Suffix appended to topic name for retry topics. */
        private String retrySuffix = "-retry";
    }

    @Data
    public static class Security {
        /** Security protocol: PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL. */
        private String protocol = "PLAINTEXT";

        /** SASL mechanism: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512. */
        private String saslMechanism;

        /** JAAS config string for SASL authentication. */
        private String saslJaasConfig;

        /** Path to truststore file (for SSL). */
        private String sslTruststoreLocation;

        /** Truststore password. */
        private String sslTruststorePassword;
    }
}

