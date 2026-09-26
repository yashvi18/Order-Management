# CLAUDE.md — E-commerce Order Management

Guidance for AI agents working in this repo.

## Build & test
- JDK 21 via SDKMAN: `source "$HOME/.sdkman/bin/sdkman-init.sh"`
- `./mvnw test` — full suite (must stay green); `./mvnw -q test -Dtest=ClassName` for one class
- `./mvnw spring-boot:run` — app on :8080 with demo data (see README for credentials)

## Architecture rules
- Package by feature under `com.ecommerce.oms` (catalog, inventory, cart, order, payment, events, fulfillment, returns).
- Controllers are thin; business rules live in `*Service`. Never return JPA entities — use records in `*Dtos`.
- Errors: throw `NotFoundException`/`BadRequestException`/`ConflictException`/`ForbiddenException` (or another
  `ApiException`); `GlobalExceptionHandler` renders RFC 7807.
- Money is `BigDecimal` scale 2 HALF_UP via `Money.of`. Never use double for money.
- Order status changes ONLY via `CustomerOrder.transitionTo` (enforced by `OrderStateMachine`).
- Stock changes ONLY via `InventoryService` (row locks in id order). Methods marked `Propagation.MANDATORY` must
  run inside the caller's transaction.
- Side effects that must not slow the request (notifications, audit, routing) go through
  `OrderEventPublisher.publish` (transactional outbox) and an `OrderEventHandler`.
- Entity attributes must not be named `order` (JPQL keyword) — use `customerOrder`.

## Testing conventions
- Integration tests extend `support.IntegrationTestBase` (real H2, DB truncated before each test, HTTP Basic via
  `as(user)`); build data with `fixtures.*`.
- The outbox scheduler is off in tests; call `outboxProcessor.processBatch()` to run the pipeline deterministically.
- TDD: write the failing test first, watch it fail, then implement.

## Workflow
- Plan: `docs/superpowers/plans/2026-09-26-ecommerce-order-management.md` — one task per commit.
- Skills used: see `docs/ai/skills/`.
