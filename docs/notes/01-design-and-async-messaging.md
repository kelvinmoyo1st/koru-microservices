# 01 - System design and why Kafka sits between the services

## Project
Koru is a fictional keyboard and accessories shop. Two Spring Boot services talk through Kafka:
Order Service takes orders, Notification Service tells the customer and the shop owner.

## Design

```mermaid
flowchart TD
  C[Customer] --> O[Order Service]
    O --> ODB[(Orders DB)]
      O --> K{{Kafka topic: order.placed}}
        K --> N[Notification Service]
          N --> NDB[(Notifications DB)]
            N --> E[Customer email and owner alert]
            ```

            Order Service saves the order first, then publishes an `OrderPlaced` event, then answers the customer.
            It never calls Notification Service and does not know it exists.

            ## Decisions and what breaks without them

            | Decision | What goes wrong without it |
            |---|---|
            | Kafka between the services | Direct HTTP couples them. See the two options below. |
            | Save the order before publishing | The customer could be told "success" for an order we never stored. |
            | Separate database per service | One service can corrupt or lock the other's data. |
            | Unique `eventId` plus a `processed_events` table | Redelivered events send duplicate notifications. |
            | Store the ID in a database, not in memory | A restart forgets it and duplicates return. |

            ## Comprehension check 1

            Scenario: Order Service calls Notification Service directly over HTTP and Notification Service is down.

            - Option 1: save the order, call Notification, retry on timeout.
              Cost: if Notification sent the email but the acknowledgement was lost, the retry sends it again (duplicate).
                The customer's request also hangs through every timeout and retry.
                - Option 2: do not save the order until Notification acknowledges.
                  Cost: the customer sees a failure and retries. The shop loses sales, because a nice-to-have (email)
                    takes down the money-maker (ordering). Nothing was saved, so retries do not create duplicate orders.
                      Duplicates only appear if Notification sent the email but its acknowledgement was lost.
                      - Kafka fixes this by holding the event until Notification is back. The order succeeds either way.

                      Scenario: the consumer crashes after sending the email but before saving the event ID.

                      - Kafka does not push and wait for replies. The consumer pulls messages and commits its offset (its bookmark) to Kafka.
                      - On restart it resumes from the last committed offset, re-reads the event, finds no ID in the database,
                        and sends the email again. The customer gets a duplicate.
                        - This cannot be fully removed: sending an email and writing a row cannot be one atomic step.
                          We accept at-least-once delivery. A rare duplicate email beats a lost one.
                            Record-first-then-send gives at-most-once, which can silently lose emails.
                            - Dedupe on `eventId`, a unique ID per event. The event type (`OrderPlaced`, `OrderCancelled`)
                              says what happened. An order ID alone would wrongly discard a later cancellation event.

                              ## Interview one-liners
                              - "Kafka gives at-least-once delivery, so my consumer is idempotent: it dedupes on a unique event ID stored in its own database."
                              - "Direct HTTP ties ordering availability to notification availability. An event bus breaks that dependency."
                              - "The consumer commits its offset to Kafka after processing. A crash in between means redelivery, not loss."

                              ## Known gaps to revisit
                              - Saving to the database and publishing to Kafka are not atomic (transactional outbox pattern, later).
                              - An API gateway in front of the services is out of scope for v1.
                              - Upstash Kafka was discontinued, so we use Apache Kafka (KRaft mode) in Docker, then Kubernetes.
                              