
# 🚀 Kafka Common Library

 

A **production-grade reusable Kafka library** for Spring Boot microservices.

Designed to simplify Kafka integration with **generic producers, consumers, event models, and observability support**.

 

---

 

## 📌 Features

 

* ✅ Generic Kafka Producer (supports any payload)

* ✅ Generic Kafka Consumer (extensible design)

* ✅ Standardized Event Model (`KafkaEvent<T>`)

* ✅ Retry with Exponential Backoff

* ✅ Dead Letter Topic (DLT) Support

* ✅ JSON Serialization/Deserialization (Jackson)

* ✅ Built-in Logging (SLF4J + MDC)

* ✅ Micrometer Metrics Support

* ✅ Spring Boot Auto Configuration Ready

* ✅ Clean, reusable, plug-and-play design

 

---

 

## 🏗️ Project Structure

 

```

kafka-common-library/

│

├── config/ # Kafka producer/consumer configuration

├── producer/ # Generic Kafka Producer

├── consumer/ # Generic Kafka Consumer (base class)

├── handler/ # Error handler + DLT publisher

├── interceptor/ # Producer interceptor (trace headers)

├── model/ # KafkaEvent<T> wrapper

├── retry/ # Retry topic configuration

└── util/ # KafkaHeaderUtil

```

 

---

 

## ⚙️ Installation

 

### 1️⃣ Build and Install the Library

 

Run this inside the `kafka-common-library` folder.

> Re-run every time you make changes to the library.

 

```bash

cd kafka-common-library

mvn clean install

```

 

Expected output:

```

[INFO] BUILD SUCCESS

[INFO] Installing kafka-common-library-1.0.0.jar to

[INFO] ~/.m2/repository/com/akashcodes/kafka-common-library/1.0.0/

```

 

---

 

### 2️⃣ Start Kafka (Docker)

 

```bash

cd kafka-common-library

 

# First time — wipe old volume

docker-compose down -v

 

# Start Kafka + Kafka UI

docker-compose up -d

 

# Verify Kafka is healthy

docker logs kafka -f

```

 

| Service | URL |

|------------|-------------------------|

| Kafka | `localhost:29092` |

| Kafka UI | http://localhost:8080 |

 

---

 

### 3️⃣ Add Dependency to Your Microservice

 

```xml

<dependency>

<groupId>com.akashcodes</groupId>

<artifactId>kafka-common-library</artifactId>

<version>1.0.0</version>

</dependency>

```

 

> `spring-kafka`, `jackson`, and `micrometer-core` are included automatically — no need to add them separately.

 

---

 

### 4️⃣ Configure application.yml

 

**Producer service:**

```yaml

spring:

application:

name: your-service-name

kafka:

producer:

properties:

spring.json.add.type.headers: true # required for deserialization

 

kafka:

common:

producer:

bootstrap-servers: localhost:29092

```

 

**Consumer service:**

```yaml

spring:

application:

name: your-service-name

 

kafka:

common:

consumer:

bootstrap-servers: localhost:29092

group-id: your-service-group # REQUIRED — must be unique per service

```

 

**Both producer + consumer:**

```yaml

spring:

application:

name: your-service-name

kafka:

producer:

properties:

spring.json.add.type.headers: true

 

kafka:

common:

topic: your-topic-name

producer:

bootstrap-servers: localhost:29092

consumer:

bootstrap-servers: localhost:29092

group-id: your-service-group # REQUIRED — must be unique per service

concurrency: 3

retry:

max-attempts: 4

initial-interval-ms: 2000

multiplier: 2.0

max-interval-ms: 10000

security:

protocol: PLAINTEXT

```

 

> ⚠️ `group-id` must be unique per microservice. If two services share the same group-id, Kafka splits messages between them instead of delivering to both.

 

---

 

### 5️⃣ Update Main Application Class

 

```java

@EnableKafka

@SpringBootApplication

@ComponentScan(basePackages = {

"com.yourcompany.yourservice", // your service package

"com.akashcodes.kafka" // library package — required

})

public class YourServiceApplication {

public static void main(String[] args) {

SpringApplication.run(YourServiceApplication.class, args);

}

}

```

 

---

 

## 🚀 Usage

 

### 🔹 Publishing Events

 

Inject `GenericKafkaProducer` into any `@Service` and call `publish()`:

 

```java

@Service

@RequiredArgsConstructor

public class OrderService {

 

private final GenericKafkaProducer kafkaProducer;

 

public void createOrder(String orderId, OrderPayload payload) {

 

KafkaEvent<OrderPayload> event = KafkaEvent.of(

"ORDER_CREATED", // eventType

"order-service", // source

payload

);

 

// Async publish (recommended)

kafkaProducer.publish("order-topic", orderId, event);

 

// Sync publish (blocks until broker confirms)

kafkaProducer.publishSync("order-topic", orderId, event);

}

}

```

 

> ⚠️ Never send JPA entities as payload. Always use a plain DTO/payload class to avoid `LazyInitializationException`.

 

---

 

### 🔹 Consuming Events

 

Extend `GenericKafkaConsumer` and override `processEvent()`:

 

```java

@Slf4j

@Service

public class OrderEventConsumer extends GenericKafkaConsumer {

 

private final OrderProcessingService orderProcessingService;

 

public OrderEventConsumer(MeterRegistry meterRegistry,

KafkaHeaderUtil kafkaHeaderUtil,

OrderProcessingService orderProcessingService) {

super(meterRegistry, kafkaHeaderUtil);

this.orderProcessingService = orderProcessingService;

}

 

@Override

@RetryableTopic(

attempts = "4",

backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),

dltTopicSuffix = "-dlt",

autoCreateTopics = "true"

)

@KafkaListener(

topics = "order-topic",

groupId = "order-service-group"

)

protected void processEvent(KafkaEvent<?> event,

String topic,

int partition,

long offset) {

 

OrderPayload payload = (OrderPayload) event.getPayload();

 

switch (event.getEventType()) {

case "ORDER_CREATED" -> orderProcessingService.handle(payload);

default -> log.warn("Unknown eventType={}", event.getEventType());

}

}

}

```

 

---

 

## 🧾 Kafka Event Model

 

```java

@Data

@Builder

@NoArgsConstructor

@AllArgsConstructor

public class KafkaEvent<T> {

private String eventId; // auto-generated UUID

private String eventType; // e.g. ORDER_CREATED

private String source; // name of the producing service

private String schemaVersion; // defaults to "1.0"

private String correlationId; // for distributed tracing

private T payload; // your business DTO

private LocalDateTime createdAt;

private int retryCount;

}

```

 

**Factory methods:**

```java

// Simple

KafkaEvent<MyPayload> event = KafkaEvent.of("EVENT_TYPE", "service-name", payload);

 

// With correlation ID

KafkaEvent<MyPayload> event = KafkaEvent.of("EVENT_TYPE", "service-name", payload, correlationId);

 

// Full builder

KafkaEvent<MyPayload> event = KafkaEvent.<MyPayload>builder()

.eventType("EVENT_TYPE")

.source("service-name")

.payload(payload)

.correlationId(UUID.randomUUID().toString())

.schemaVersion("2.0")

.build();

```

 

---

 

## 📥 Imports Reference

 

```java

// Producer

import com.akashcodes.kafka.producer.GenericKafkaProducer;

import com.akashcodes.kafka.model.KafkaEvent;

 

// Consumer

import com.akashcodes.kafka.consumer.GenericKafkaConsumer;

import com.akashcodes.kafka.model.KafkaEvent;

import com.akashcodes.kafka.util.KafkaHeaderUtil;

```

 

---

 

## 🔁 Retry & Dead Letter Topic Flow

 

```

Attempt 1 → your-topic (0ms — normal processing)

Attempt 2 → your-topic-retry (2s — first retry)

Attempt 3 → your-topic-retry (4s — second retry)

Attempt 4 → your-topic-retry (8s — third retry)

Failed → your-topic-dlt (dead letter — manual inspection)

```

 

---

 

## 📊 Observability

 

* Micrometer metrics on every publish and consume (`kafka.producer.publish`, `kafka.consumer.process`)

* MDC logging with `correlationId`, `eventId`, `eventType` on every message

* Easily integrable with Prometheus + Grafana

 

---

 

## 🔧 All Supported Configuration Properties

 

```yaml

kafka:

common:

topic: # default topic name

producer:

bootstrap-servers: # default: localhost:29092

acks: # default: all

retries: # default: 3

enable-idempotence: # default: true

compression-type: # default: snappy

batch-size: # default: 16384

linger-ms: # default: 1

consumer:

bootstrap-servers: # default: localhost:29092

group-id: # REQUIRED — no default

auto-offset-reset: # default: earliest

enable-auto-commit: # default: false

concurrency: # default: 3

max-poll-records: # default: 100

trusted-packages: # default: com.akashcodes.*

retry:

max-attempts: # default: 3

initial-interval-ms: # default: 1000

multiplier: # default: 2.0

max-interval-ms: # default: 10000

dlt-suffix: # default: -dlt

retry-suffix: # default: -retry

security:

protocol: # default: PLAINTEXT

```

 

---

 

## 🧪 Testing

 

```bash

mvn test

```

 

---

 

## 📦 Build

 

```bash

mvn clean install

```

 

---

 

## ❗ Common Errors

 

| Error | Fix |

|---|---|

| `No type information in headers` | Add `spring.json.add.type.headers: true` in producer yml |

| `Beans not found` | Check `@ComponentScan` includes `com.akashcodes.kafka` |

| `LazyInitializationException` | Don't send JPA entities — use a DTO |

| `Connection refused on 9092` | Use port `29092` in `bootstrap-servers` |

| `BUILD FAILURE` after library change | Run `mvn clean install` in library folder first |

| `CLUSTER_ID mismatch` | Run `docker-compose down -v` then `docker-compose up -d` |

 

---

 

## 🌍 Roadmap

 

* [x] Generic Producer & Consumer

* [x] Retry with Exponential Backoff

* [x] Dead Letter Topic (DLT)

* [x] Kafka Headers Support

* [x] Micrometer Metrics

* [x] Spring Boot Auto Configuration

* [ ] Schema Registry Integration

* [ ] Distributed Tracing (OpenTelemetry)

* [ ] Multi-module structure

 

---

 

## 🤝 Contributing

 

Contributions are welcome!

 

1. Fork the repo

2. Create feature branch

3. Commit changes

4. Open Pull Request

 

---

 

## 📄 License

 

This project is licensed under the MIT License.

 

---

 

## 👨‍💻 Author

 

**Akash Maurya**

Software Engineer | Java Full Stack Developer | Spring Boot | Microservices | Kafka

 

---

 

## ⭐ Support

 

If you find this project useful, please ⭐ the repository.


---
