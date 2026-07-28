# Addendum: Dine-in Multi-Round Ordering (加菜 / One-Bill Settlement)

**Status:** Accepted
**Date:** 2026-07-28
**Amends:** [2026-07-13-restaurant-ordering-backend-design.md](2026-07-13-restaurant-ordering-backend-design.md)
**Nature:** Business-model change. Where this addendum and the base design conflict,
this addendum wins. Everything not mentioned here is unchanged.

## 0. Why this addendum exists

The base design models one table session as **one order, one payment, cart frozen
on submit**. Real dine-in QR ordering is **加菜**: a party at a table submits
dishes in several rounds during the meal, then settles the whole bill **once**
before leaving. The product owner confirmed this is the target behavior
("一客一结账" = one party, one settlement — but multiple ordering rounds).

Two other decisions were confirmed and do **not** change the base design:

- **Backend API only.** No customer or staff frontend in this repo. Front/back
  decoupling is achieved through versioned REST + OpenAPI. A future customer H5
  and kitchen board consume these APIs.
- **Keep the heavy engineering stack.** MySQL + Redis + RocketMQ + Outbox +
  two instances + Prometheus/Jaeger + Testcontainers all stay. The goal remains a
  defensible concurrency-correctness portfolio; the multi-round model makes the
  concurrency story richer, not simpler.

## 1. What this changes at a glance

| Concern | Base design | This addendum |
|---|---|---|
| Orders per session | Exactly one | **Many** — one per round (加菜) |
| Cart on submit | Frozen forever | **Cleared and reused** for the next round; frozen only at checkout |
| Payment attaches to | The single order | **The session bill** (sum of all valid rounds) |
| "Claim" step | First order submission | **Checkout** (`OPEN → PENDING_PAYMENT`) |
| Session close | On payment of the one order | On payment of the **bill** |

## 2. Superseded sections of the base design

- **§3 Non-goals:** remove "Multiple order batches inside one table session" and
  "Multiple settlement modes; version one is one-order-one-payment". Multiple
  rounds per session and bill-level settlement are now **in scope**.
- **§4.1 Table session:** the rule "at most one order per session" is replaced by
  §3 below. The single-active-session-per-table invariant (generated column +
  unique index) is **retained unchanged**.
- **§4.2 One-order-one-payment:** replaced by §4 (Bill and settlement) below.
- **§6.1 Durable data:** `orders` loses `unique(table_session_id)`; see §5.
- **§8.1 Customer APIs:** replaced by §7 below.
- **§9.2 Order submission:** the single freeze-and-reconcile flow is split into
  "per-round submission" (§8.1) and "checkout freeze" (§8.2).

## 3. Revised business rules — rounds

A **round** (`order_round`) is one batch of dishes a party submits. It is what the
kitchen sees as an incoming ticket ("来单").

- While the session is `OPEN`, any active participant may submit a round.
- Each round submission is idempotent on an `Idempotency-Key`; a retry never
  creates a second round.
- Submitting a round validates current menu items, snapshots name + unit price,
  reserves inventory for that round, and creates one `order_round` in status
  `CONFIRMED` (payment is deferred to checkout, so there is no per-round
  `PENDING_PAYMENT`).
- After a successful submission the shared Redis cart is **cleared** (version
  bumped) so the party can start a fresh round. The cart is not permanently
  frozen.
- Each round runs the kitchen fulfillment state machine independently:

```text
CONFIRMED -> PREPARING -> READY -> COMPLETED
     |
     +-> CANCELLED        (staff cancels a round; releases that round's stock once)
```

- A round may be submitted only when `session.status = 'OPEN'`. If a checkout is
  in progress (`PENDING_PAYMENT`), round submission is rejected with
  `SESSION_IN_CHECKOUT` (409).

## 4. Revised business rules — bill and settlement

The **bill** is the session-level aggregate that gets paid once.

- A bill total is the sum of all **non-cancelled** rounds' item totals.
- Any active participant (or staff) may request checkout.
- Checkout atomically transitions the session `OPEN → PENDING_PAYMENT` via CAS;
  concurrent checkout requests race and exactly one wins. The winner snapshots the
  bill amount and starts a **simulated** payment for that amount.
- Payment may be retried until success or expiry.
- **On payment success:** the bill becomes `PAID`, the session becomes `CLOSED`,
  and an `BillPaid` event is emitted for reporting/kitchen. Already-completed
  rounds are unaffected; rounds still in progress remain in the kitchen queue.
- **On payment failure/expiry, or explicit "cancel checkout":** the session
  returns `PENDING_PAYMENT → OPEN`, the bill snapshot is discarded, and the party
  may add more rounds or retry checkout. Any reserved-but-unpaid state is a no-op
  here because inventory was already reserved per round, not at checkout.
- Staff with `table_session:close` may force-close an `OPEN` or `PENDING_PAYMENT`
  session with a reason; this cancels all non-terminal rounds (releasing their
  stock exactly once) and voids any pending bill. Closing a closed session is
  idempotent.

### Session state machine (authoritative)

```text
OPEN ──(submit round, repeatable)──────────────> OPEN
OPEN ──(checkout, CAS)─────────────────────────> PENDING_PAYMENT
PENDING_PAYMENT ──(payment success)────────────> CLOSED
PENDING_PAYMENT ──(payment fail/expiry/cancel)─> OPEN
OPEN | PENDING_PAYMENT ──(staff force close)───> FORCE_CLOSED
```

## 5. Revised persistence model (deltas only)

| Table | Change |
|---|---|
| `table_session` | Add `bill_amount DECIMAL` (nullable; set at checkout), keep version + generated active marker. States: `OPEN`, `PENDING_PAYMENT`, `CLOSED`, `FORCE_CLOSED`. |
| `order_round` (was `orders`) | **Drop** `unique(table_session_id)`. Add `round_no INT` with `unique(table_session_id, round_no)`. Status is the kitchen state machine, not payment. |
| `order_item` | Unchanged; FK now references `order_round`. |
| `payment` | FK moves from order to **`table_session`** (one active payment attempt per session at a time). Amount = session `bill_amount` snapshot. |
| `inventory_ledger` | Unchanged mechanism; reservations/releases are now keyed per **round** operation id. |
| `outbox_event` | Event types become `OrderRoundCreated`, `BillPaid`, `SessionClosed`, `RoundCancelled`. |

`round_no` is allocated per session (e.g. `MAX(round_no)+1` inside the submission
transaction, guarded by the `unique(table_session_id, round_no)` constraint so a
race surfaces as a retryable conflict rather than a duplicate).

## 6. Redis (deltas only)

```text
cart:{sessionId}          Hash menuItemId -> quantity   (cleared after each round submit)
cart:meta:{sessionId}     version, state(OPEN|LOCKED), lastRoundNo
cart:op:{sessionId}:{opId} cached op result (TTL)
```

The cart `state` is `LOCKED` only during an in-progress checkout; a successful
round submit leaves it `OPEN` with an empty hash and a bumped version.

## 7. Revised customer API surface

```text
POST   /api/v1/table-sessions/join
GET    /api/v1/table-sessions/{sessionId}
GET    /api/v1/menu-items
GET    /api/v1/table-sessions/{sessionId}/cart
PUT    /api/v1/table-sessions/{sessionId}/cart/items/{menuItemId}
DELETE /api/v1/table-sessions/{sessionId}/cart
POST   /api/v1/table-sessions/{sessionId}/orders        # submit ONE round (repeatable)
GET    /api/v1/table-sessions/{sessionId}/rounds        # this party's rounds + status
POST   /api/v1/table-sessions/{sessionId}/checkout      # OPEN -> PENDING_PAYMENT, snapshot bill
GET    /api/v1/table-sessions/{sessionId}/bill          # current bill (rounds + total)
POST   /api/v1/sessions/{sessionId}/payment-attempts    # simulated payment for the bill
```

Round submission requires an `Idempotency-Key` header and `expectedCartVersion`.
Checkout requires an `Idempotency-Key`. The simulated-provider callback path
(§8.2 of base design) is unchanged and still HMAC-signed.

## 8. Revised concurrency flows

### 8.1 Per-round submission (replaces base §9.2, part 1)

In one MySQL transaction, guarded by durable idempotency:

1. Verify `session.status = 'OPEN'` (conditional; else `SESSION_IN_CHECKOUT` /
   `INVALID_STATE_TRANSITION`).
2. Read the frozen cart snapshot for this `operationId`/idempotency key.
3. Validate menu items, load server-side prices, snapshot into `order_item`.
4. Reserve inventory per item with the conditional `UPDATE ... WHERE available >=`.
5. Allocate `round_no` and insert `order_round` (`CONFIRMED`) + items + ledger rows.
6. Persist idempotency result; insert `OrderRoundCreated` outbox row.
7. After commit, clear the Redis cart (bump version) only if the version matches.

Because inventory is reserved per round and the round is immediately `CONFIRMED`,
there is no cross-system freeze to reconcile per round — the reconciler only
covers the checkout freeze in §8.2.

### 8.2 Checkout + settlement (replaces base §9.2, part 2, and §9.4)

1. CAS `table_session` `OPEN → PENDING_PAYMENT`; loser gets the winner's bill.
2. Snapshot `bill_amount = SUM(non-cancelled rounds)`; lock the Redis cart.
3. Create one `payment` for the session; emit nothing to kitchen yet.
4. Simulated success callback (HMAC, idempotent on provider event id) transitions
   `payment -> SUCCEEDED`, session `PENDING_PAYMENT -> CLOSED`, bill `-> PAID`,
   inserts `BillPaid` + `SessionClosed` outbox rows. Duplicate callbacks are no-ops.
5. Payment expiry (RocketMQ delay) or explicit cancel transitions session
   `PENDING_PAYMENT -> OPEN` and unlocks the cart; success vs expiry race is
   resolved by the session-state condition so exactly one wins.

### 8.3 New required concurrency scenarios (add to base §13.2)

- N concurrent round submissions with distinct idempotency keys on one `OPEN`
  session produce N rounds with contiguous `round_no` and correct stock movement.
- Replaying one round's idempotency key yields the same single round.
- Concurrent checkout requests produce exactly one bill and one payment.
- A round submission racing a checkout: either the round commits before the CAS
  (bill includes it) or is rejected with `SESSION_IN_CHECKOUT`; never a partial.
- Payment success racing expiry on the bill yields exactly one terminal session
  state.

## 9. New/changed error codes (add to base §10)

| HTTP | Code | Meaning |
|---|---|---|
| 409 | `SESSION_IN_CHECKOUT` | Round submission attempted while a checkout is in progress |
| 409 | `NO_BILLABLE_ROUNDS` | Checkout attempted with zero non-cancelled rounds |

## 10. Delivery sequence impact

The base design's vertical-slice order (§17) is unchanged **except** slices 6–7:

- Slice 6 becomes: Redis shared cart with **per-round clear**, per-round idempotent
  submission, inventory reservation, and `round_no` allocation.
- Slice 7 becomes: bill aggregation, checkout CAS, simulated bill payment, timeout
  cancellation back to `OPEN`, and the per-round kitchen state machine.

The foundation slice (foundation-implementation-plan.md) is unaffected and
proceeds as written.
