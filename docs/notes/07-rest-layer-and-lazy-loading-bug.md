# 07 - REST layer, and a real lazy-loading bug caught before it shipped

## The pieces
- `PlaceOrderRequest` / `OrderResponse`: Java **records** - compact immutable data holders. One line
  gives a constructor, accessors (`sku()`, not `getSku()`), `equals`, `hashCode`, `toString`.
    No entity (`Order`, `Product`) is ever returned from the controller directly - only these DTOs.
      The API's shape should never be welded to the database schema.
      - `@Valid` on the controller parameter runs `PlaceOrderRequest`'s `@NotBlank`/`@Min` checks before the
        method body executes. A failure throws `MethodArgumentNotValidException` automatically.
        - `@RestControllerAdvice` (`GlobalExceptionHandler`) catches exceptions from any controller in one
          place instead of try/catch in every method: `404` for not-found, `409 Conflict` for both stock
            conflicts, `400` for bad input.
            - `NotFoundException` (abstract) is the shared parent of `ProductNotFoundException` and
              `OrderNotFoundException`, so one handler method covers both.

              ## A real bug we found before it ever reached a client
              `OrderController.getOrder()` originally called `orderService.findById(id)` (returning the raw `Order`
              entity), then `OrderResponse.from(order)` in the controller - which calls `order.getProduct().getSku()`.

              ### Why this failed, precisely
              - `OrderService.findById()` had no `@Transactional` of its own.
              - `orderRepository.findById(id)` (Spring Data's generated method) carries its *own* built-in
                `@Transactional(readOnly = true)`, scoped tightly to just that one repository call. It opens and
                  fully commits **inside** that call, before control even returns to `OrderService.findById()`.
                  - By the time `OrderResponse.from(order)` ran in the controller, no transaction or session existed
                    anywhere. `product` (`@ManyToOne(LAZY)`) was a genuine empty Hibernate proxy with a dead session
                      reference. Calling `.getSku()` on it threw `LazyInitializationException`.

                      ### Why this looked fine earlier, in `placeOrder()`, but wasn't the same situation
                      In `StockReservationService.reserve()`, `product` comes from `productRepository.findBySku(sku)` -
                      loaded as the *root* of that query, fully populated, no proxy involved at all. `new Order(product, ...)`
                      then hands that real, already-built object straight into the `Order`'s field in plain Java code.
                      Hibernate never touches that field to swap in a proxy. It isn't "lazy loading that happened to work" -
                      there was no lazy loading involved in that path to begin with. `getOrder()` is a different case: the
                      `Order` is built entirely from a database row Hibernate read itself, so the lazy association really is
                      an empty proxy this time.

                      ### Proving it before fixing it
                      Wrote `OrderServiceLazyLoadingTest` asserting `LazyInitializationException` *was* thrown - a
                      "documents the current bug" test, expected to pass while the bug exists. Confirmed green, proving the
                      reasoning was right before touching any code.

                      ### The fix, and why relocating code alone wasn't enough
                      Moved `OrderResponse.from(order)` inside `OrderService.findById()`, and added
                      `@Transactional(readOnly = true)` directly on that method. The annotation is what actually matters:
                      it makes the *service's own* proxy open a transaction for the whole method body - repository call and
                      DTO mapping together - rather than Spring Data's call-scoped one. Moving the code without the
                      annotation would have just relocated the same bug one stack frame higher, not fixed it.

                      Flipped the test to a real regression test: same scenario, asserting the SKU now comes back correctly
                      with no exception. Confirmed via the SQL trace itself - a second `select ... from products ... where
                      id=?` now appears and completes successfully, inside the same test run, proving the proxy initialized
                      before anything closed.

                      ## The trade-off we accepted: service importing from web
                      `OrderService` now imports `OrderResponse` from the `web` package - backwards from the usual
                      dependency direction (web should depend on service). Two concrete costs:
                      - `OrderServiceTest`, a pure unit test of retry-loop control flow, now implicitly depends on web-layer
                        types it has no real use for.
                        - A second entry point later (a Kafka consumer, a scheduled job, `NotificationService` calling in)
                          would be forced to use an HTTP-shaped DTO literally named `OrderResponse`, or duplicate near-identical
                            DTOs just to avoid the awkward import.
                            Accepted for now as the smaller cost versus leaking entities past the service boundary; worth
                            revisiting if more callers appear.

                            ## Interview one-liners
                            - "I caught a `LazyInitializationException` risk with a test that documented the bug before I fixed it,
                              not by guessing from the design."
                              - "The fix wasn't moving code - it was giving `findById()` its own `@Transactional` boundary wide
                                enough to cover both the load and the DTO mapping."
                                - "DTOs never cross back from a controller into a service test by accident - when they do, it's a
                                  signal the dependency direction inverted somewhere."

                                  ## Known gaps to revisit
                                  - No Kafka yet - `StockReservationService.reserve()` doesn't publish anything after confirming an order.
                                  - `service` importing from `web` is a trade-off, not a final answer - revisit if a second caller appears.
                                  