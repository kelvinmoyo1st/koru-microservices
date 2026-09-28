# 02 - Scaffolding order-service and how Spring finds your classes

## What we generated
Spring Initializr, Maven, Java 25, Spring Boot 4.1.x, package `com.koru.order`.

| Dependency | Why we need it | What breaks without it |
|---|---|---|
| `web` | Spring MVC plus embedded Tomcat | No HTTP server, no REST endpoints |
| `validation` | `@NotBlank`, `@Min` on request bodies | A customer can order -5 keyboards |
| `data-jpa` | Hibernate plus Spring Data repositories | We write SQL and mapping by hand |
| `h2` | In-memory database, zero setup | No database in dev (Postgres replaces it before deploy) |
| `actuator` | `/actuator/health` | Kubernetes cannot tell if a pod is alive |

Left out on purpose: Kafka (its own commit) and Lombok (plain records and classes so every line is explainable).

## Key ideas
- A starter is a bundle of dependencies. The parent POM pins compatible versions for all of them.
- `@SpringBootApplication` = `@SpringBootConfiguration` + `@ComponentScan` + `@EnableAutoConfiguration`.
- `@ComponentScan` starts at the main class's package and walks down its sub-packages. It never goes up or sideways.
  It registers classes marked `@Component` (and `@Service`, `@Repository`, `@Controller`, `@RestController`) as beans.
  - `@EnableAutoConfiguration` inspects the classpath and properties and creates beans when conditions match
    (`@ConditionalOnClass`, `@ConditionalOnMissingBean`). It backs off when you define your own bean.
    - `contextLoads()` starts the whole Spring context, so it is a cheap smoke test: if any bean cannot be built, it fails.

    ## Comprehension check 2

    1. Remove `h2`, keep `data-jpa`, set no database URL.
       JPA needs a `DataSource`. Auto-configuration builds one only if it finds an embedded database or a URL.
          With neither, startup fails with a message like "Failed to configure a DataSource".
             Fixes: add an embedded database, or set `spring.datasource.url` and a driver.
             2. A `@RestController` in `com.koru.web` (sibling of `com.koru.order`) gives 404 on every call, and the app starts fine.
                - It is never scanned, so it is never a bean, so no URL is mapped to it.
                   - Startup errors come from beans Spring tries to build and fails. An unscanned class is never a candidate.
                      - The request finds no handler, so Spring answers 404. The status blames the client, but the server is misconfigured.
                         - Fixes: move the class under `com.koru.order` (standard, near zero cost), or use
                              `@SpringBootApplication(scanBasePackages = {...})` (cost: a list to maintain; forget an entry and the bug returns).
                              3. Main class is `com.koru.order.OrderServiceApplication`:
                                 - `com.koru.order.controller.OrderController` is found (sub-package).
                                    - `com.koru.orderhistory.HistoryService` is not found (sibling folder, similar name is irrelevant).
                                       - `com.koru.OrderUtil` is not found (parent package: scanning only goes down).

                                       ## Interview one-liners
                                       - "Component scanning is mechanical: it walks down from the main class's package. Anything outside is invisible."
                                       - "Auto-configuration is conditional. It creates a DataSource only if it finds an embedded DB or a URL, and it backs off if I define my own."

                                       ## Known gaps to revisit
                                       - `spring.jpa.open-in-view` warning: decide and set it explicitly when we write the first entity.
                                       - Mockito self-attach warning on Java 25: harmless for now, revisit if tests get noisy.
                                       