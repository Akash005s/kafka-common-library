# 🚀 Kafka Common Library

A **production-grade reusable Kafka library** for Spring Boot microservices.
Designed to simplify Kafka integration with **generic producers, consumers, event models, and observability support**.

---

## 📌 Features

* ✅ Generic Kafka Producer (supports any payload)
* ✅ Generic Kafka Consumer (extensible design)
* ✅ Standardized Event Model (`KafkaEvent<T>`)
* ✅ JSON Serialization/Deserialization (Jackson)
* ✅ Built-in Logging (SLF4J)
* ✅ Micrometer Metrics Support
* ✅ Spring Boot Auto Configuration Ready
* ✅ Clean, reusable, plug-and-play design

---

## 🏗️ Project Structure

```
kafka-common-library/
│
├── config/        # Kafka configuration classes
├── producer/      # Generic Kafka Producer
├── consumer/      # Generic Kafka Consumer
├── model/         # KafkaEvent, common DTOs
├── util/          # Utility classes
```

---

## ⚙️ Installation

### 1️⃣ Add Dependency

For local usage only:

```xml
<dependency>
    <groupId>com.akashcodes</groupId>
    <artifactId>kafka-common-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

```bash
mvn clean install
```

---

## 🚀 Usage

### 🔹 Generic Kafka Producer

```java
@RequiredArgsConstructor
@Service
public class OrderService {

    private final GenericKafkaProducer producer;

    public void sendOrderEvent() {
        KafkaEvent<String> event = KafkaEvent.<String>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ORDER_CREATED")
                .payload("Order Created Successfully")
                .timestamp(System.currentTimeMillis())
                .build();

        producer.send("order-topic", event);
    }
}
```

---

### 🔹 Sample Controller

```java
@RestController
@RequiredArgsConstructor
public class KafkaTestController {

    private final GenericKafkaProducer producer;

    @GetMapping("/send")
    public String send() {
        producer.send("test-topic", "Hello Kafka");
        return "Message Sent";
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
    private String eventId;
    private String eventType;
    private T payload;
    private long timestamp;
}
```

---

## 📊 Observability

* Micrometer metrics support
* Logging with correlation-friendly structure
* Easily integrable with Prometheus + Grafana

---

## 🔧 Configuration (application.yml)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

kafka:
  common:
    producer:
      retries: 3
```

---

## 🧪 Testing

```bash
mvn test
```

Includes:

* Kafka producer/consumer tests
* Integration testing with embedded Kafka

---

## 📦 Build

```bash
mvn clean install
```

---

## 🌍 Roadmap

* [ ] Retry & Backoff Strategy
* [ ] Dead Letter Queue (DLQ)
* [ ] Kafka Headers Support
* [ ] Schema Registry Integration
* [ ] Distributed Tracing Support
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
