# 04 - ProductRepository, and proving the optimistic lock actually works

## ProductRepository
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    }
    ```
    - No implementation is written. Spring Data generates a runtime proxy backed by `EntityManager`.
    - `findBySku` is **query derivation**: Spring Data parses the method name itself
      (`findBy` + `Sku` → `select p from Product p where p.sku = ?1`). No JPQL is hand-written.
      - `Optional<Product>` forces callers to handle "not found" explicitly instead of risking NPE.

      ## Core vocabulary
      - **Entity**: a `@Entity`-annotated class. One instance = one row. (`Product` is our only entity so far.)
      - **EntityManager**: the object that talks to the database for one unit of work
        (`persist`, `merge`, `find`, `flush`, `clear`). `JpaRepository.save()` wraps it.
        - **Persistence context**: the `EntityManager`'s private map of `(entity type, ID) → one Java object`
          for the current unit of work. Also called the first-level cache / identity map. Calling `find()`
            twice for the same ID inside it returns the *same object reference* both times.
            - `entityManager.clear()` empties that map without touching the database — the only way to force a
              later `find()` to build a genuinely separate object.

              ## A real bug we hit, and why it matters more than the fix
              First version of the concurrency test cleared the context after loading `requestA` and `requestB`,
              but not again before `requestA`'s save. Result: `requestB.decreaseStock(1)` threw
              `IllegalStateException: Insufficient stock`, not the expected `OptimisticLockException`.

              Trace of what actually happened:
              1. `requestB` loaded — now sitting in the persistence context.
              2. `requestA.decreaseStock(1)` → local field change only.
              3. `saveAndFlush(requestA)` → `merge()` looks for a managed object with that ID **before** doing
                 anything else. It finds `requestB` still there (context was never cleared after loading it) and
                    copies `requestA`'s new `stockQuantity` (0) straight onto it.
                    4. `requestB.decreaseStock(1)` runs against a `stockQuantity` that is now silently 0, not 1 — the
                       guard fires before the optimistic-lock machinery ever gets a chance to run.

                       **An AI-suggested "fix" for this exact error (seed more stock so the numbers add up) was wrong.**
                       It didn't touch the actual cause. Worse: with more stock, the merge-mutation bug still fires, but
                       now `requestB`'s guard doesn't trip — so the second `saveAndFlush` inside `assertThrows` succeeds
                       cleanly (since `requestB` had been kept perpetually managed and in sync), and `assertThrows` itself
                       fails because nothing was thrown. The fix that traded a loud failure for a silent false-positive test.
                       **Lesson: read the mechanism the error implicates, not just the words in the error message.**

                       Real fix: `entityManager.clear()` after loading `requestB` too, and again right after `requestA`'s
                       save, so every `merge()` always starts from an empty context — genuinely detached, isolated objects.

                       ## Reading the fixed test's SQL trace (`spring.jpa.show-sql=true` earns its keep here)
                       ```
                       select ...   -- requestA loaded
                       select ...   -- requestB loaded
                       select ...   -- merge() reloads current row before applying requestA's changes
                       update ...   -- requestA's write succeeds; DB row now at version=1
                       select ...   -- merge() reloads current row before applying requestB's changes
                                    -- test ends here — no second UPDATE was ever sent
                                    ```
                                    Only one `update` appears. `merge()` reloads the row via `SELECT` before writing, and compares the
                                    freshly-loaded `version` (1) against the detached entity's own `version` (0) right there in memory.
                                    Finding a mismatch, it throws immediately — it never bothers issuing an `UPDATE` it already knows
                                    would affect zero rows. That's a genuine Hibernate implementation detail, visible only by reading
                                    the actual SQL trace instead of trusting the stack trace summary alone.

                                    ## Interview one-liners
                                    - "Spring Data repositories are runtime proxies over `EntityManager` — I never write the implementation."
                                    - "Query derivation parses the method name into JPQL; I only write custom `@Query` when the name would get unreadable."
                                    - "I proved my optimistic lock actually works with a `@DataJpaTest` that deliberately detaches two
                                      copies of the same row and updates both — not just a design doc claiming it works."
                                      - "Hibernate's `merge()` can detect a stale version off the reload `SELECT` alone, before ever
                                        attempting the `UPDATE` — I saw this directly in the SQL trace, not in documentation."

                                        ## Known gaps to revisit
                                        - Retry logic for `OptimisticLockException` doesn't exist yet — belongs in `OrderService`.
                                        - No real concurrency (two actual threads) yet — this test proves the *mechanism*, not throughput
                                          under real contention. A load test is a later, optional stretch goal.
                                          