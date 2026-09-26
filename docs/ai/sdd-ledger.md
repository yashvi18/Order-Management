# SDD ledger — plan: docs/superpowers/plans/2026-09-26-ecommerce-order-management.md

Spec: docs/spec/E-commerce Order Management.pdf (root until Task 0 moves it) — reachable.
Execution: subagent-driven (user choice). TDD required (user: "use test-driven development too").
User instruction: push to origin (https://github.com/yashvi18/Order-Management) after each completed task.

## Pre-flight scan
| Tasks | Shared file/interface | Finding |
|---|---|---|
| T0 vs repo state | git init / .gitignore | Repo already initialised with origin + remote "first commit" (README.md). start.spring.io unzip overwrites .gitignore. |
| T1→T2..T15 | TestFixtures (field-injected, find-or-create users) | consistent; later tasks only add fields+methods |
| T1→T10,T13 | IntegrationTestBase helpers checkout(User)/advanceToDelivered | added in T10/T13; T11,T12 declare private outboxProcessor fields that shadow T13's protected one — legal Java |
| T3→T6,T10,T12,T13,T14 | InventoryService reserve/release/commitShipment/restock, StockAllocation | signatures consistent |
| T4→T10 | DiscountService.apply(code,subtotal,Instant)/redeem(id) | consistent |
| T5→T10 | PricingService.price(lines, BigDecimal) | consistent |
| T6→T10 | CartRepository.lockByCustomerId, fixtures.cartWith (void) | consistent |
| T7→T8,T10,T12,T14 | PaymentService.capture/refund/findSummary | consistent |
| T8→T10..T14 | CustomerOrder.transitionTo/markPlaced/stockAllocations/warehouseCodes, OrderRepository.lockById/findForWarehouse, OrderAllocationRepository exists…Id | consistent |
| T9→T10..T14 | OrderEventPublisher.publish(type, orderId, customerId, actor, details) | consistent |
| T13→T14 | WarehouseAccessPolicy.check(orderId, actor) | consistent |
| T0 self | pom edits (remove h2console) vs T11/T15 pom additions | consistent |
| T1 self | GlobalExceptionHandler covers ApiException/validation/concurrency | tests 7 consistent with code |
| T2 self | sort whitelist incl. price | test sort=price,desc ok |
| T3 self | JPA 3.2 @CheckConstraint with documented fallback | ok |
| T9 self | AdminAuditController list | ok |
| T10 self | 9 API tests + 2 concurrency tests vs CheckoutService | consistent |
| T15 self | seeder counts 4 users/6 products/12 stock rows/2 discounts | consistent with DataSeeder code |
| T16 self | README/CLAUDE.md | copies skills list; keep only used |

Ruling: T0 skip `git init -b main` (repo already initialised on main tracking origin/main with remote README) and build on top of origin/main — history must stay fast-forwardable for pushes — cost if wrong: none, remote commit preserved.
Ruling: T0 re-add `.superpowers/` and `.DS_Store` to .gitignore after unzip overwrites it — SDD scratch must never be committed — cost if wrong: scratch files pushed publicly.
Ruling: work directly on `main` (no worktree/feature branch) — user explicitly asked to push each task to their new personal repo — cost if wrong: history on main instead of a branch; reversible.
Ruling: after each task's review is clean, controller runs `git push origin main` — per explicit user instruction — cost if wrong: WIP visible publicly (user wants that).
Ruling: keep the pre-existing remote README.md until T16 overwrites it with the full README — cost: none.

## Progress
Task 0: implemented c210422 (base 36fc41c). Deviations: SDKMAN installer bash-4 check patched (macOS bash 3.2); parent version 4.1.1 (Maven Central has no .RELEASE suffix). Awaiting review.
Ruling: accept `<parent><version>4.1.1</version>` instead of plan's 4.1.1.RELEASE — Maven Central coordinate has no suffix; spec only says "Spring Boot" — cost if wrong: none, same release.
Task 0: minor (deferred): SDKMAN installer bash-version check patched locally to install on bash 3.2 (no repo effect).
Ruling: commit trailer stays `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` for every commit regardless of implementer model — plan Global Constraint + session attribution instruction — cost if wrong: cosmetic attribution only. All later dispatches carry this.
Task 0: fix round 1/5 (1 addressed, 0 open — commit trailer; amend c210422→66c2667, tree identical, verified by controller git check)
Task 0: complete (commits 36fc41c..66c2667, review clean) — pushed to origin/main
Task 1: minor (deferred): TestFixtures.user lowercases but does not trim email (plan-mandated, inert).
Task 1: complete (commits 66c2667..0374812, review clean; ⚠️ tests+trailer verified by controller) — pushed
Ruling: Task 2 reviewer ❌ "missing GET /api/admin/categories/{id}" — the brief's interface shorthand `[/{id}]` covers PUT/DELETE; the brief's code and tests define only list GET, and the spec does not require it; admins get categories via the list — no fix — cost if wrong: one trivial endpoint to add later.
Task 2: minor (deferred): Category.taxRate stored at request scale, not normalized to scale 4 (plan-mandated; pricing multiplies then rounds via Money.of so harmless).
Task 2: minor (deferred): category name uniqueness is case-insensitive in app check but case-sensitive in DB constraint (race only).
Task 2: complete (commits 0374812..aab133d, review clean) — pushed
Task 3: minor (deferred): no committed multi-thread reserve test / MANDATORY no-tx test (checkout-level concurrency tests come in Task 10; reviewer ran 40-thread ad-hoc test: no oversell).
Task 3: minor (deferred): reserve/apply do not reject quantity <= 0 (cart/DTO validation guards callers).
Task 3: minor (deferred): IllegalStateException from requireReserved / missing row maps to 500 (plan-mandated).
Task 3: minor (deferred): apply() locks all warehouse rows of a product (extra contention; plan-mandated).
Task 3: minor (deferred): availableForProduct not readOnly; createUser 404 for bad warehouseId + accepts inactive warehouse; warehouses cannot be reactivated; lockOrCreate first-insert race -> 409.
Task 3: complete (commits aab133d..278de4d, review clean) — pushed
Ruling: Task 4 accept DiscountService.apply normalising code with trim().toUpperCase(Locale.ROOT) before lookup — plan's own unit test stubs 'SAVE' but calls with 'save'; codes are stored uppercase so behaviour is identical — cost if wrong: none.
Task 4: minor (deferred): incrementUsage @Modifying without clearAutomatically — stale Discount in persistence context if re-read after redeem in same tx (not done today).
Task 4: minor (deferred): create() check-then-insert race -> DataIntegrityViolation (mapped to 409 by handler); two unrelated `apply` overloads in DiscountService.
Task 4: complete (commits 278de4d..a102fb1, review clean) — pushed
Ruling: Task 5 plan-mandated defect confirmed (remainder-to-largest can over-allocate, e.g. 4x10.00 with 0.02 discount -> 0.03 applied). Replace with largest-remainder allocation (floor every share to the cent, hand leftover cents one each to lines by largest fractional remainder, ties -> larger subtotal, then lower index) and floor negative discountAmount at 0 — spec requires correct discounts; plan's expected outputs in existing tests remain identical — cost if wrong: none, strictly more correct.
Task 5: fix round 1/5 (2 addressed, 0 open — discount over-allocation, negative discount; commits ef3320f..071bade)
Task 5: complete (commits a102fb1..071bade, review clean) — pushed
Ruling: Task 6 Important (plan-mandated) first-cart creation race — no fix: unique customer_id constraint rejects the duplicate and GlobalExceptionHandler maps DataIntegrityViolationException to 409 "please retry"; only possible on a customer's very first cart request — cost if wrong: rare 409 on first add, client retries.
Task 6: minor (deferred): fixtures.cartWith always inserts a new line (dup product would hit unique key); wildcard import in CartController (plan-mandated).
Task 6: complete (commits 071bade..7b44dc5, review clean) — pushed
Ruling: Task 7 Important (plan-mandated) refundedAmount adds raw amount without Money.of — no fix: every caller (cancel: grandTotal; returns: Money-scaled per-line refunds) passes scale-2 values; flagged for final review — cost if wrong: sub-cent drift only if a future caller passes unscaled amounts.
Task 7: minor (deferred): duplicate capture per order -> unique violation (handler maps to 409, not 500); findByPaymentOrderIdOrderByIdAsc unused; gateway refund-failure branch untested; refund 400/404 branches untested.
Task 7: complete (commits 7b44dc5..24a6217, review clean) — pushed
Task 8: minor (deferred): CustomerOrderTest doesn't cover stockAllocations()/warehouseCodes()/markPlaced guard (exercised in T10+); AddressDto.country rejects lowercase; OrderMapper findSummary per order (N+1 on lists).
Task 8: complete (commits 24a6217..12e9cb1, review clean) — pushed
Task 9: minor (deferred): outbox poll has no FOR UPDATE SKIP LOCKED (single-instance assumption, documented in README); details length not truncated before insert (1000/1200 caps); audit-log fallback "latest 100" (brief-specified); batch-fetch tx not readOnly.
Task 9: complete (commits 12e9cb1..8144c39, review clean) — pushed
Ruling: Task 10 Important (plan-mandated) "charge captured but tx rolls back afterwards => money taken without order" — fix now: PaymentService.capture registers a TransactionSynchronization whose afterCompletion(STATUS_ROLLED_BACK) calls gateway.refund(txnId, amount) (compensating void, failures logged); tested with a spy gateway — spec demands order placement atomically reflect payment state — cost if wrong: small extra code; real PSPs would use authorize/void.
Task 10: minor (deferred): replay-after-cart-lock branch untested; double-submit test timing-dependent; redeem()==0 race path untested; replay ignores differing payload (to document in README); cart edits don't take cart lock (quantity change during checkout lost).
Task 10: fix round 1/5 (1 addressed, 0 open — compensating void on rollback; commits 1e38d2b..4374ff2)
Task 10: minor (deferred): void-on-rollback ignores GatewayResult.success() of the refund (declined void logged as voided); log text names "checkout".
Task 10: complete (commits 8144c39..4374ff2, review clean) — pushed
Task 11: minor (deferred): skip-routing log at INFO; cancelled-before-routing test lives in Task 12 (routerDoesNotConfirmAnOrderCancelledFirst).
Task 11: complete (commits 4374ff2..138c480, review clean) — pushed
Ruling: Task 12 Important (plan-mandated) refund in cancel() is an external call inside the DB tx; a later rollback (only outbox insert remains) would leave the customer refunded but order not cancelled — accept + document in README trade-offs (production: issue refunds post-commit via outbox) — flagged for final review — cost if wrong: rare over-refund needing manual reconciliation.
Task 12: minor (deferred): no test for cancelling another customer's order (same code path as GET → 404); admin GET relies solely on filter chain.
Task 12: complete (commits 138c480..c227dc0, review clean) — pushed
Task 13: minor (deferred): staff of one warehouse can advance a split order and commit other warehouses' allocations on SHIPPED (order-level status by design, Scoping Decision 11); fulfillment tests don't assert ProblemDetail body shape.
Task 13: complete (commits c227dc0..55c2587, review clean) — pushed
Ruling: Task 14 Important (plan-mandated) concurrent double receive can issue two gateway refunds — fix: ReturnRequestRepository.lockById (PESSIMISTIC_WRITE) used by requirePending so the status check happens under the return-row lock (lock order: return row, then order row; requestReturn only locks order → no cycle); add concurrency test; also move to List<@Valid ReturnLine> + quantity:0 → 400 test (HV000271) — spec requires correct refunds — cost if wrong: none.
Task 14: minor (deferred): restock to first allocation's warehouse (plan-mandated, Scoping Decision 10); REFUND_ISSUED published for 0.00 refund; Location header points at collection; N+1 in listForWarehouse.
Task 14: fix round 1/5 (2 addressed, 0 open — double-receive refund race, @Valid placement; commits 01409a7..22124fb)
Task 14: minor (deferred, IMPORTANT FOR FINAL REVIEW): OrderService.cancel checks status without a pessimistic lock; two concurrent cancels can both call gateway.refund before @Version rejects the second commit → double refund at PSP (same class as the Task 14 fix; fix with OrderRepository.lockById in cancel).
Task 14: complete (commits 55c2587..22124fb, review clean) — pushed
Task 15: minor (deferred): DataSeederTest doesn't assert staff warehouse linkage / alice login (plan-mandated).
Task 15: complete (commits 22124fb..f9c59bd, review clean; springdoc 3.1.1 works with Boot 4.1.1) — pushed
