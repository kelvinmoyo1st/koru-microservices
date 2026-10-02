# 06 - OrderService: retry logic, the proxy trap, and two kinds of test

## The classes
- `OrderRepository` - plain `JpaRepository<Order, Long>`, no custom queries yet.
- `ProductNotFoundException` - thrown when a SKU doesn't exist.
- `StockReservationService.reserve(sku, quantity)` - `@Transactional`. Looks up the product,
  calls `decreaseStock()`, saves it, builds and saves a confirmed `Order`. One atomic unit of work.
  - `OrderService.placeOrder(sku, quantity)` - not transactional itself. Loops up to `MAX_ATTEMPTS`
    times, calling `stockReservationService.reserve(...)` and catching `ObjectOptimisticLockingFailureException`.

    ## Why retry logic and `@Transactional` live in two separate beans
    `@Transactional` works by wrapping the bean in a **proxy**. A call only gets intercepted — transaction
    opened, committed/rolled back — when it arrives *from outside the object*, through the proxy.

    If the retry loop and `reserve()` were methods on the *same* class, and the loop called
    `this.reserve(...)` directly, that call would be a plain internal Java call that never touches the
    proxy. `@Transactional` would be silently ignored — not just on retries, but on every call, including
    the first one, since `placeOrder()` (not transactional) is the only method anyone calls from outside.
    No error, no warning. It just quietly never opens a transaction. Splitting the loop into `OrderService`
    and the transactional work into `StockReservationService` guarantees every call to `reserve()` — from
    any caller — goes through the real bean and gets a real transaction.

    ## Tracing the retry loop against the test's stub sequence
    `OrderServiceTest`'s first test stubs `reserve()` as: throw, throw, return.

    | Pass | `attempt` | `reserve()` call | Threw? | `attempt >= 3`? | Outcome |
    |---|---|---|---|---|---|
    | 1 | 1 | 1st stub: throws | Yes | No | loop back |
    | 2 | 2 | 2nd stub: throws | Yes | No | loop back |
    | 3 | 3 | 3rd stub: returns | No | never checked | returns immediately |

    Key point: the loop exits via the success branch before ever reaching the "give up" check. This test
    succeeds on call 3 because the stub was configured to throw exactly twice — not because of
    `MAX_ATTEMPTS`. As long as `MAX_ATTEMPTS >= 3`, changing it to 4, 5, or 100 has zero effect on this test.

    Contrast with the second test (`givesUpAfterMaxAttemptsAndPropagatesException`): its mock has **one**
    stub behavior (`.thenThrow(...)` alone, nothing chained after), applying to every call forever. Here
    the call count is driven entirely by `MAX_ATTEMPTS`. Changing it from 3 to 5 while leaving
    `verify(..., times(3))` unchanged breaks the test, because the real call count becomes 5.

    ## What `verify(mock, times(n)).method(...)` actually does
    It isn't checked live, "at" some call during execution. It's a single assertion that runs *after* the
    test method body finishes, replaying Mockito's internal log of every call made to that mock, asking:
    was this exact call made **exactly n times** — no more, no less? A mismatch (3 expected, 5 actual)
    throws Mockito's own verification failure, and JUnit reports the test as failed.

    ## What `@Mock` / `@InjectMocks` actually do (and don't)
    - `@Mock` builds a dynamically generated fake object shaped like `StockReservationService`. It is not
      the real class. Its real method bodies never run - only what you explicitly stub with
        `when(...).thenThrow(...)` / `.thenReturn(...)` happens. The real `@Transactional` annotation on the
          real class is irrelevant here, because the real method is never reached.
          - `@InjectMocks` is a Mockito mechanism, unrelated to Spring's `@Autowired`. It inspects `OrderService`'s
            constructor and hands it the `@Mock` fake. It builds a real `OrderService` with a fake collaborator.
            - Net effect: no Spring context, no database, no real transaction, no real `@Version` check ever runs.
              This test proves Java control flow only.

              ## Why that matters: two tests that prove two different, non-overlapping things
              `OrderServiceTest` stubs the exception by hand:
              ```java
              .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L))
              ```
              This proves: *if* `reserve()` throws this exception, the loop reacts correctly. It says nothing about
              whether the real `reserve()` actually produces that exception under real contention. If `@Version` were
              deleted from `Product` entirely, this test would still pass, unchanged, forever - a green suite sitting
              on a silently broken lock.

              `ProductRepositoryOptimisticLockingTest` (an earlier phase) is the one that proves the lock itself is
              real: real `@DataJpaTest`, real H2, two genuinely detached objects, a real `merge()` racing against a
              real version column, throwing because Hibernate actually found a mismatch - not because a test told it to.

              **Neither test can stand in for the other.** The mock test proves the loop's control flow. The
              repository test proves the underlying locking mechanism. A project needs both.

              ## Interview one-liners
              - "I split transactional work into its own bean because `@Transactional` relies on proxy interception,
                which only fires on external calls - a same-class internal call silently skips it."
                - "My unit test for the retry loop uses Mockito to isolate control flow from persistence; a separate
                  `@DataJpaTest` proves the optimistic lock itself is real, not just assumed."
                  - "`verify(mock, times(n))` is a strict post-hoc assertion on the call log, not a live check during execution."

                  ## Known gaps to revisit
                  - No REST endpoint yet to actually call `placeOrder` over HTTP.
                  - No mapping yet from `ProductNotFoundException` / exhausted retries to proper HTTP status codes.
                  - No validation yet on the incoming quantity/SKU (the `validation` dependency is still unused).
                  