# Koru Microservices

A distributed e-commerce order-and-notification system, built to go deep on the real failure modes of
event-driven microservices rather than stay shallow across many features. Two services, one Kafka
topic, and a deliberate focus on what actually breaks in production: concurrent stock updates,
duplicate message delivery, and the atomicity gap between a database write and a message publish.

## Architecture

    Customer
       |
       v
    Order Service  --(writes)-->  order_db (PostgreSQL)
       |
       |  transactional outbox + scheduled relay
       v
    Kafka topic: order.placed
       |
       v
    Notification Service  --(writes)-->  notification_db (PostgreSQL)
       |
       v
    "Notify customer" (logged; real email/SMS integration not yet built)

Each service owns its own database - no shared schema, no cross-service foreign keys. They communicate
only through Kafka.

## Why it's built this way

- **Optimistic locking on Product.stockQuantity** (@Version), with a retry loop in OrderService
  bounded to 3 attempts. Two customers buying the last unit at once is a real race condition, proven
  with a repository test that deliberately detaches two copies of the same row and updates both.
- **Transactional outbox pattern**, not a naive dual write. A relational database and Kafka have no
  shared commit log - writing to both directly means there's always a window where one succeeds and the
  other fails. The outbox writes the order and an "intent to publish" row in the same local transaction,
  then a separate scheduled relay delivers it to Kafka independently, retrying on failure.
- **Idempotent consumer**, not just "exactly-once" wishful thinking. Kafka guarantees at-least-once
  delivery, so duplicates are a real possibility, not a hypothetical - proven with an automated test that
  publishes the same event twice against a real broker and asserts it's only processed once. Dedup is a
  database-enforced primary key on the event ID, not an application-level check that could be skipped.
- **Separate databases per service**, enforcing the core microservices principle that no service can
  reach into another's data directly.

## Tech stack

Java 25, Spring Boot 4.1.1, Spring Data JPA, Spring Kafka, PostgreSQL 17, Apache Kafka 4.1.1 (KRaft mode,
no Zookeeper), Docker (multi-stage builds), Kubernetes (Deployments/Services, run locally via minikube),
JUnit 5, Mockito.

## Running it

Docker Compose (Kafka, PostgreSQL, both services):

    ./start-stack.sh

This also reapplies a host networking rule needed specifically in GitHub Codespaces-style sandboxed
environments - documented in docs/notes/11-docker-dual-listeners-and-the-codespaces-networking-bug.md.

Kubernetes (minikube):

    minikube start
    docker build -t koru-microservices-order-service:latest ./order-service
    docker build -t koru-microservices-notification-service:latest ./notification-service
    minikube image load koru-microservices-order-service:latest
    minikube image load koru-microservices-notification-service:latest
    kubectl apply -f k8s/postgres.yaml
    kubectl apply -f k8s/kafka.yaml
    kubectl apply -f k8s/order-service.yaml
    kubectl apply -f k8s/notification-service.yaml

Try it:

    kubectl port-forward service/order-service 18080:8080 &
    curl -X POST localhost:18080/api/products -H "Content-Type: application/json" -d '{"name":"Koru TKL Keyboard","sku":"KORU-TKL-01","priceCents":12999,"stockQuantity":5}'
    curl -X POST localhost:18080/api/orders -H "Content-Type: application/json" -d '{"sku":"KORU-TKL-01","quantity":1}'

## Testing

Unit and repository tests run against H2 for speed and isolation (./mvnw test in each service). A
separate category of test runs against a real Kafka broker - not mocked - proving the system actually
works, not just that the logic is theoretically correct:
- OutboxRelayLiveKafkaTest (order-service): confirms the relay genuinely publishes to a live broker.
- OrderPlacedListenerLiveKafkaTest (notification-service): publishes the same event twice and asserts
  it's processed exactly once.

## Known gaps, and why they're sequenced this way

- **No authentication yet.** Deliberately sequenced after the core distributed-systems problems -
  concurrency, atomicity, duplicate delivery - since those are where the architecture actually gets
  tested. JWT validation at an API gateway in front of both services is the natural next step, enforced
  once at the edge rather than duplicated per service.
- **Notification is a log line**, not a real email/SMS integration - the event-driven mechanics are the
  point of this project, not a third-party delivery provider.
- **Single-currency, two-decimal assumption** in how money is stored (priceCents as an integer).

## Project structure

    koru-microservices/
    |-- order-service/        Spring Boot service: products, orders, stock, outbox
    |-- notification-service/ Spring Boot service: Kafka consumer, dedup, notifications
    |-- k8s/                  Kubernetes manifests
    |-- docs/notes/           Phase-by-phase build notes - design decisions, bugs found and fixed,
    |                         with real command output as evidence, not just descriptions
    |-- docker-compose.yml    Local dev stack: Kafka, Postgres, both services
    |-- start-stack.sh        One-command startup, including environment-specific fixes

docs/notes/ is worth reading for the full story, including real bugs hit and fixed along the way - a
Hibernate lazy-loading trap, a Kafka hairpin NAT issue specific to Kubernetes, and the dual-write
problem this whole architecture exists to avoid.
