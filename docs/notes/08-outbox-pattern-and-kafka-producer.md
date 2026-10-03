# 08 - Transactional outbox, the Kafka relay, and a real debugging saga

## The problem this phase solves
A relational database and Kafka are two separate systems with no shared atomic commit between them
(the dual-write problem). Writing to both in one naive step means there's always a window where one
succeeds and the other fails - and no ordering of the two calls removes that window, it only changes
which side ends up lying about reality.

Two concrete failure traces walked through before writing any code:
- DB commits, Kafka publish then fails -> client gets a raw error, retries the same request, and
  since nothing is idempotent at the HTTP layer, a second real order and a second stock decrement
    happen. The first order's notification is still never sent.
    - Kafka publish succeeds, then something later in the same method throws and the DB transaction rolls
      back -> a customer can hold a "confirmed" notification for an order that doesn't exist in the
        database at all, and the stock was never actually reserved.

        ## The fix: transactional outbox
        Instead of writing to the database and Kafka, write to the database **twice**, in the same local
        transaction: once for `Order`, once for a plain `OutboxEvent` row recording intent to publish. Both
        are ordinary rows in the same database, reached through the same connection, inside the same
        `BEGIN...COMMIT` - exactly the scenario ACID's atomicity guarantee was built for. A separate process
        (the relay) later reads unpublished rows and sends them to Kafka, independently, after the order is
        already safely durable.

        **Why this works when DB+Kafka didn't, precisely:** atomicity requires a shared commit log. Two tables
        in one database share one. A database and Kafka do not - making that work would require a real
        distributed transaction (two-phase commit), which Kafka doesn't practically support. The outbox never
        asks the two systems to agree with each other; it only asks the database to agree with itself.

        ## Design choices in `OutboxEvent`
        - Flat typed columns, no `@ManyToOne`, no lazy fields at all - deliberately sidesteps the
          `LazyInitializationException` trap from the previous phase. Nothing here can go stale across a
            transaction boundary because nothing here is a lazy association.
            - `eventId` is a fresh UUID, independent of `orderId` - same idempotency-key reasoning from the very
              first comprehension check of this project, now actually implemented.

              ## The relay (`OutboxRelay`)
              `@Scheduled(fixedDelay = 2000)` reads up to 50 unpublished rows, tries to publish each one, marks it
              published on success, leaves it alone on failure.

              **Why each event gets its own `try/catch` inside the loop, not one around the whole loop:** with the
              catch inside, an exception on event #3 is handled right there and the loop simply continues to #4 -
              ordinary Java control flow. If the catch wrapped the whole loop instead, that same exception would
              unwind out of the loop entirely via normal propagation, and every event after #3 in that batch would
              never even be attempted - not because anything was wrong with them, but purely because of where the
              failure happened to land in iteration order.

              **The failure mode this honestly introduces: a duplicate message in Kafka is still possible**, and the
              exact design that makes failures recoverable is the same mechanism that causes it:
              1. The relay sends event A. Kafka durably stores it.
              2. The process crashes right after the send succeeds, but before `markPublished()` + `save()` run.
              3. `publishedAt` is still `null` in the database.
              4. The next scheduled run re-reads that same row (query only looks at `publishedAt IS NULL`) and
                 sends it again.

                 Two messages, same `eventId`, both sitting in the topic. This isn't a bug in the design - it's the
                 deliberate trade-off of favoring "maybe duplicated" over "maybe lost forever." But it only remains safe
                 if something downstream actually checks `eventId` before acting on it. Nothing does yet - the
                 `eventId`-based dedup table from Check 1 was only ever designed, not built. It belongs in
                 `notification-service`, the actual consumer, and is next.

                 ## The debugging saga: a duplicate dependency and a lesson about verifying diagnoses
                 Adding `spring-boot-starter-kafka` initially produced a totally unrelated-looking failure (`invalid
                 boolean value`), caused by `cat >>` fusing two properties lines together with no newline between them
                 (heredoc appends exactly what's inside it - no automatic newline is inserted). Fixed with a precise,
                 evidence-checked `python3` replace rather than guessing at the file's contents.

                 Immediately after, a full test run showed `Spring Boot :: v3.4.1` in the banner - the first time any
                 log in this entire project had shown anything but `4.1.1`. Rather than trust that it was fine because
                 tests still passed, we required proof: `dependency:tree`, which confirmed everything genuinely
                 resolved to `4.1.1` (so the banner was a one-off, not a real mismatch) but also surfaced a real,
                 separate problem - a literal duplicate `spring-boot-starter-data-jpa-test` dependency declaration.

                 Tracing it back: that dependency was added weeks earlier to fix the `@DataJpaTest` package-move error.
                 **The likely truth, only visible now: it was probably never actually missing.** Spring Initializr
                 already adds the matching modular test-starter automatically for every main dependency selected on
                 Boot 4 - it was solving exactly this problem from the original scaffold. What actually fixed that
                 earlier error was the import-path correction, not the dependency addition, which just sat there
                 silently duplicated (Maven only warns on an exact duplicate, it doesn't fail) until this phase
                 surfaced it.

                 **The actual lesson, independent of Spring or Kafka:** a fix "working" is not proof the diagnosis
                 behind it was correct. Two different changes landed in the same commit back then; only one was load-
                 bearing, and there was no way to tell which from the outcome alone. Removing the duplicate,
                 re-confirming the version banner twice, and re-running the full suite before committing - rather than
                 assuming "tests pass, ship it" - is what actually caught this.

                 ## Interview one-liners
                 - "I implemented the transactional outbox pattern instead of a naive dual-write, because atomicity
                   requires a shared commit log, and a database and a message broker don't have one."
                   - "My relay favors eventual duplicates over silent loss - and that's only safe because the consumer
                     side is designed to dedupe on event ID, not because duplicates can't happen."
                     - "A green test suite told me a fix worked two different times this project - once it was actually
                       right, once the real fix was a different line entirely. I don't trust a passing build as proof of
                         which line mattered without checking."

                         ## Known gaps to revisit
                         - The relay has never been proven against a real, running Kafka broker - only unit/Spring-context
                           tested with the application not actually talking to any broker over the network yet.
                           - `notification-service` and its `eventId` dedup table don't exist yet - the duplicate-event risk this
                             phase introduced is currently uncaught by anything downstream.
                             