# 05 - Order entity: lazy loading and enum storage

## The entity
`Order` holds a `@ManyToOne` to `Product`, a `quantity`, a `status`, and a `createdAt` timestamp.
Generated DDL confirmed the foreign key:
```sql
alter table if exists orders add constraint FK... foreign key (product_id) references products
```

## Decision 1: `fetch = FetchType.LAZY`, written explicitly
JPA's default for `@ManyToOne` is `EAGER` — easy to forget since `@OneToMany`'s default is lazy.

- **What "join" means concretely**: one SQL query pulling matching rows from both tables at once —
  `SELECT o.*, p.* FROM orders o JOIN products p ON o.product_id = p.id`. `EAGER` generates this
    automatically on every load, whether the caller needs the product or not.
    - **Where "loading" goes**: the DB returns raw column values (a JDBC `ResultSet`). Hibernate turns
      those into real Java objects (hydration).
        - `EAGER`: the join already returned the product's columns, so Hibernate builds a fully-populated
            real `Product` object immediately.
              - `LAZY`: no join happened, so Hibernate plants a **proxy** — a placeholder subclass of `Product`
                  holding no real data, just a live reference to the open session. Calling `.getName()` on it fires
                      a second query right then to fill itself in.
                      - **Eager is wasteful, lazy is dangerous — these are different failure modes.**
                        - Eager: `Order` was fully built at load time, so `getProduct().getName()` works even *after* the
                            transaction closes — it's just reading a field off an object already in memory. The cost is paid
                                up front on every load, needed or not (10,000 orders in a report = 10,000 unneeded joins).
                                  - Lazy: the query is deferred until the getter is called. If that call happens after the
                                      transaction/session has closed, Hibernate throws `LazyInitializationException` — one of the most
                                          common real Spring bugs. This is exactly why `spring.jpa.open-in-view=false` matters: it forces
                                              all such access to happen inside an explicit `@Transactional` service method, where it's safe.

                                              ## Decision 2: `@Enumerated(EnumType.STRING)`, not the default
                                              Unspecified, Hibernate stores enums as `ORDINAL` — a plain integer index.

                                              | Ordinal | Before | After inserting `PAUSED` |
                                              |---|---|---|
                                              | 0 | `WAITING` | `WAITING` |
                                              | 1 | `STARTED` | `STARTED` |
                                              | 2 | `STOPPED` | **`PAUSED`** |
                                              | 3 | — | `STOPPED` |

                                              An existing row never changes — it keeps storing the raw number `2` forever. Before the change, the
                                              app read `2` as "Stopped." After deploying code with `PAUSED` inserted at index 2, that same
                                              untouched `2` is now read as "Paused." No exception anywhere — a silent reinterpretation of old data.
                                              Concrete consequence: someone sees a machine as merely "paused" (resumable) when it actually failed
                                              and stopped, and acts on the wrong assumption. `STRING` stores the literal word, so it's immune to
                                              reordering.

                                              ## Note on the generated DDL
                                              H2 printed the enum check constraint alphabetically — `('CONFIRMED','FAILED','PENDING')` — not in
                                              declared order (`PENDING, CONFIRMED, FAILED`). That's purely how H2 renders the constraint; storage
                                              uses the literal string regardless, independent of declaration order (the whole point of `STRING`).

                                              ## Design discipline carried over from `Product`
                                              No `setStatus()`. State changes only through `markConfirmed()` / `markFailed()`, so an invalid
                                              status transition can never be assigned directly from outside the class.

                                              ## Interview one-liners
                                              - "I make `@ManyToOne` fetch type explicit rather than relying on JPA's `EAGER` default, because the
                                                default silently causes N+1-style over-fetching at scale."
                                                - "I store enums as `STRING`, not `ORDINAL`, because ordinal storage ties data meaning to declaration
                                                  order — inserting a new constant anywhere but the end silently corrupts existing rows."
                                                  - "`LazyInitializationException` happens because the proxy's query is deferred until the getter is
                                                    called; if that's outside the transaction, there's no session left to run it."

                                                    ## Known gaps to revisit
                                                    - No `OrderService` yet — nothing calls `decreaseStock()` and `save(order)` together as one unit.
                                                    - No retry logic for `OptimisticLockException` when placing an order under contention.
                                                    - No REST endpoint yet to actually place an order.
                                                    