# Polaris Emulator security and robustness audit

**Review date:** 2026-07-16
**Scope:** `Emulator/src/main/java`, database schema/migrations, configuration defaults, and the packet/HTTP/RCON entry points in this repository
**Method:** manual static review with targeted searches for arithmetic, currency mutations, ownership and authorization checks, packet-controlled allocation/loops, SQL boundaries, session lifecycle, null handling, and transaction/concurrency hazards. High-severity candidates were subsequently re-traced from input through every visible validation and persistence layer.

## Executive summary

The previously reported unbounded catalog count is partly mitigated: `CatalogBuyItemEvent` now clamps the normal packet count to 1–100. That is useful, but it does **not** close the broader vulnerability class. Catalog prices and balances are still signed 32-bit integers, price multiplication is still performed as `int`, and a calculated price of zero or less bypasses both the affordability check and debit. Catalog administration permits prices up to 1,000,000,000, so overflow is reachable with valid server-side catalog data and a normal allowed purchase count.

Catalog items are also inserted into the database before payment and before all validation succeeds. A specially configured multi-component offer that fails late can therefore leave database-persistent items owned by the buyer without charging them. The earnings center has a similar partial-commit problem when it is enabled and configured with mixed rewards: currencies are granted before SQL-backed rewards, and a later failure deletes the claim marker without rolling back the earlier grant.

After tracing exploitability and compatibility requirements through the complete implementation, this review retains **16 reportable issues**. The initial candidates assigned SEC-07 and SEC-16 did not meet the threshold for a Polaris vulnerability and were removed. Those identifiers remain intentionally unused so references to the confirmed findings do not change.

| Severity | Count | Main themes |
|---|---:|---|
| High | 3 | catalog/club arithmetic and unbounded allocation |
| Medium | 9 | conditional partial commits/overflow, non-atomic balances, session replay/revocation, and marketplace consistency |
| Low | 4 | narrow resume-time ban race, privileged catalog-state bypass, malformed crafting packets, and CORS hardening |

“Configuration-dependent” does not mean harmless: it means exploitation needs a catalog/configuration/database value that the emulator accepts but which does not exist in the shipped data. Those findings should still be fixed in code because imported catalogs, staff tooling, plugins, and later content changes routinely alter those values.

## Remediation status

The findings below describe the audited `main` branch before remediation. The listed remediation pull requests were rechecked on 2026-07-17; none should be treated as deployed until it is merged and any listed database migration is applied.

**Compatibility invariant:** remediation must be self-contained in Polaris, including Polaris-owned database migrations. Existing CMSs, clients, proxies, packet formats, and external authentication flows must continue working without code or behavior changes. A finding remains open if the only available design would force an external integration to adapt.

| Finding | Status | Pull request / remaining work |
|---|---|---|
| SEC-01 | Fixed in open PR | [#365](https://github.com/duckietm/Polaris-Emulator/pull/365) rejects negative/overflowing catalog totals and uses checked quantity arithmetic. |
| SEC-02 | Fixed in open PR | [#365](https://github.com/duckietm/Polaris-Emulator/pull/365) prevalidates orders, reserves payment atomically, tracks every created resource, and compensates failed delivery. |
| SEC-03 | Fixed in open PR | [#365](https://github.com/duckietm/Polaris-Emulator/pull/365) uses checked gift/subscription arithmetic, reserves payment before delivery, and refunds failed grants. |
| SEC-04 | Fixed in open PR | [#369](https://github.com/duckietm/Polaris-Emulator/pull/369) retains the unique claim marker after any partial grant, closing repeatable inflation. A missing suffix is a fail-closed reliability event, not a replay vulnerability. |
| SEC-05 | Fixed in open PR | [#365](https://github.com/duckietm/Polaris-Emulator/pull/365) checks club price, point, day, and seconds multiplication before mutation. |
| SEC-06 | Fixed in open PR | [#366](https://github.com/duckietm/Polaris-Emulator/pull/366) bounds count and payload bytes before allocation and adds a handler rate limit. |
| SEC-08 | Fixed in open PR | [#368](https://github.com/duckietm/Polaris-Emulator/pull/368) rejects wallet overflow, overdraft, and negative balances under the currency lock. |
| SEC-09 | Fixed in open PR | [#368](https://github.com/duckietm/Polaris-Emulator/pull/368) uses checked `long` aggregation for marketplace claims and both sides of room trades, with wallet-headroom validation before ownership transfer. |
| SEC-10 | Fixed in open PRs | [#368](https://github.com/duckietm/Polaris-Emulator/pull/368) serializes paid custom-badge quota/payment and provides exact wallet debits; [#365](https://github.com/duckietm/Polaris-Emulator/pull/365) applies an atomic purchase gate and combined debit to catalog, gift, and club flows. |
| SEC-11 | Fixed in open PR | [#371](https://github.com/duckietm/Polaris-Emulator/pull/371) gives only Polaris-minted SSO tickets a short expiry. Existing CMS tickets with NULL expiry remain compatible and unchanged. |
| SEC-12 | Fixed in open PR | [#372](https://github.com/duckietm/Polaris-Emulator/pull/372) revokes access/remember sessions on logout and credential change, including direct password changes detected through password binding. |
| SEC-13 | Fixed in open PR | [#373](https://github.com/duckietm/Polaris-Emulator/pull/373) reuses fresh-login account, IP, and available machine-ID checks before a parked session can resume. |
| SEC-14 | Fixed in open PR | [#368](https://github.com/duckietm/Polaris-Emulator/pull/368) locks offer/item rows, reserves exact payment, and commits sold state plus durable ownership in one transaction before publishing inventory state. |
| SEC-15 | Fixed in open PR | [#370](https://github.com/duckietm/Polaris-Emulator/pull/370) applies the shared configured origin allowlist before emitting credentialed CORS headers. |
| SEC-17 | Fixed in open PR | [#366](https://github.com/duckietm/Polaris-Emulator/pull/366) adds room/item/altar null guards and bounded ingredient lists validated against remaining packet bytes. |
| SEC-18 | Fixed in open PR | [#365](https://github.com/duckietm/Polaris-Emulator/pull/365) applies one enabled/rank/club page-access policy to direct normal and gift purchases. |

### Pull request verification matrix

The exact heads below were rechecked on 2026-07-17. Each listed PR was open, mergeable, ready for review (`isDraft=false`), and began with `Finding and fix produced by OpenAI Codex.` GitHub reported no configured checks on these branches, so the complete Maven suite was run locally at every listed commit.

| PR | Exact tested head | Finding coverage | Complete Maven suite |
|---|---|---|---|
| [#365](https://github.com/duckietm/Polaris-Emulator/pull/365) | `ac044d86` | SEC-01, SEC-02, SEC-03, SEC-05, catalog portion of SEC-10, SEC-18 | Pass |
| [#366](https://github.com/duckietm/Polaris-Emulator/pull/366) | `f8619ea4` | SEC-06, SEC-17 | Pass |
| [#368](https://github.com/duckietm/Polaris-Emulator/pull/368) | `9b156b4b` | SEC-08, SEC-09, SEC-10 custom-badge/wallet portion, SEC-14 | Pass |
| [#369](https://github.com/duckietm/Polaris-Emulator/pull/369) | `88e5ee2a` | SEC-04 | Pass |
| [#370](https://github.com/duckietm/Polaris-Emulator/pull/370) | `20372fd7` | SEC-15 | Pass |
| [#371](https://github.com/duckietm/Polaris-Emulator/pull/371) | `54bda9cd` | SEC-11 | Pass |
| [#372](https://github.com/duckietm/Polaris-Emulator/pull/372) | `5c20d1be` | SEC-12 | Pass |
| [#373](https://github.com/duckietm/Polaris-Emulator/pull/373) | `552b2de6` | SEC-13 | Pass |

PR [#367](https://github.com/duckietm/Polaris-Emulator/pull/367) is intentionally closed and provides no current remediation coverage. Its last head was also tested before closure, but a passing test suite is not evidence that its SSO design was proportionate or should be merged.

### Scope conclusions and exact boundaries

- **SEC-04 is security-complete without an outbox:** retaining the claim marker makes partial delivery fail closed and prevents the demonstrated repeatable currency grant. An outbox could automate reconciliation of a missing reward suffix, but that is reliability work rather than an open inflation vulnerability.
- **SEC-11 remains deliberately narrow:** only tickets minted by Polaris receive the new expiry. NULL-expiry tickets written by existing CMSs retain their established behavior, so no external deployment is broken.
- **SEC-13 is a narrow timing race:** it requires a valid logged-in account, parking during the reconnect grace window, a ban applied while parked, and a resume before expiry. It is retained as Low because the ban command otherwise attempts immediate disconnection and the bypass can persist after a successful resume.

## Revalidation and reachability

The second-pass trace changed the original assessment. In particular, the previously stated “furniture followed by an already-owned badge” example for SEC-02 was invalid and has been removed. That late badge return only applies when there is one base-item type, so it cannot leave a different furniture component behind. Other late-failure paths still establish the underlying partial-commit issue, but with narrower preconditions; SEC-02 is therefore Medium rather than High.

The shipped SQL was also checked, not just the Java limits. Across 11,061 parsed stock `catalog_items` rows, the highest credit price is 5,000, the highest points price is 25,000, and the highest `amount` is 50. The two stock club offers cost 50 and 120 credits, and the configured special gift-wrap price is 10. Those values do **not** trigger SEC-01, SEC-03, or SEC-05. The earnings center is shipped disabled with currency rewards set to zero. These flaws become exploitable only after accepted administration values, direct database changes, imports, or feature configuration introduce the stated preconditions.

| Finding | Upstream validation traced | Downstream result | Shipped-data reachability |
|---|---|---|---|
| SEC-01 | Packet count clamps to 1–100; page/rank/item checks; multi-buy requires `haveOffer` | Price stays `int`; non-positive wrapped price skips affordability and debit; no later price validation | Not triggered by shipped catalog; built-in catalog admin accepts a sufficient price |
| SEC-02 | Page/rank/item checks and several type-specific checks | `createItem` inserts immediately; later type/data failures have no rollback | Requires a specially composed mixed bundle/failure order |
| SEC-03 | Recipient/page/wrapper/giftability/stock checks | Wrapped negative total passes comparison and skips debit; no later total validation | Not triggered by shipped prices/configuration |
| SEC-04 | Unique claim row prevents concurrent duplicate claims | Later SQL failure deletes claim row but does not undo an earlier in-memory currency grant | Feature disabled and rewards zero in shipped SQL |
| SEC-05 | Club page/offer membership and count clamp are enforced | Negative wrapped price passes comparisons and `giveCredits(-total)` adds credits | Not triggered by the two shipped offers |
| SEC-06 | Requires an authenticated packet; frame limit and normal exception handling were checked | Allocation happens from the four-byte count before content validation; `OutOfMemoryError` is not an `Exception` | Reachable without catalog/config changes |
| SEC-11 | Password/remember authentication is required to mint a ticket | Every validator accepts NULL expiry; built-in minting never writes expiry; successful game login intentionally does not consume it | Reachable when using built-in auth with the shipped nullable schema |
| SEC-18 | Page loading, page-content request, gift purchase, and normal purchase were compared | All pages/items are loaded; page-content and gift requests reject disabled pages, but normal purchase checks only rank | Shipped SQL has 32 affected items, but all corresponding pages require rank 4, 6, or 7 |

## Controlled local verification recipes

Run these only against an isolated checkout and disposable database. Capture the relevant user balance, inventory, offer/claim rows, and authentication state before and after each test. Several recipes intentionally require non-stock configuration or fault injection; that condition is part of the finding and must not be omitted when reporting a result.

| Finding | Local verification procedure | Expected result on audited `main` |
|---|---|---|
| SEC-01 | Create a normal catalog offer whose accepted configured unit price is `1_000_000_000`, give the test user less than that amount, and submit a permitted multi-buy count of `3`. Compare inventory and balance before/after. | Signed `int` multiplication wraps negative; the non-positive total can skip affordability/debit while items are created. Stock catalog values do not reproduce this. |
| SEC-02 | In a disposable catalog, create a mixed offer with an ordinary furniture component and a later component that deterministically fails validation (or inject a failure immediately after the first successful `createItem`). Attempt purchase, reload inventory from SQL, and inspect balance. | A previously inserted item row can remain owned by the buyer even though the overall purchase returns before payment. Component ordering must be controlled because the base-item collection is a `HashSet`. |
| SEC-03 | Configure a high-priced giftable offer and/or gift-wrap fee so checked mathematical addition should exceed `Integer.MAX_VALUE`; send a valid gift purchase and inspect recipient items and sender balance. Separately use an excessive configured subscription duration. | Gift total or duration wraps in `int`; the gift/subscription mutation can occur without the correct debit or with a corrupted timestamp. Stock values do not reproduce this. |
| SEC-04 | Enable earnings in a disposable database and configure one claim category with an immediately applied currency reward followed by an invalid/failing SQL-backed item or badge reward. Claim the same period twice. | The first reward is applied, the later reward fails, the claim marker is removed, and the successful prefix can be granted again. The shipped feature is disabled with zero rewards. |
| SEC-05 | Configure a club offer price near `1_000_000_000` and submit a valid count of `3`; record credits and subscription state. | The total wraps negative, the affordability comparison succeeds, and negating the total can credit the user while extending club state. Stock club offers do not reproduce this. |
| SEC-06 | Authenticate a test client and send the jukebox track-data packet with a declared count near `Integer.MAX_VALUE` but no corresponding IDs. Use a memory-limited disposable JVM so the host is not endangered. | `new ArrayList<>(count)` is attempted before remaining-byte validation and can raise `OutOfMemoryError` or severe allocation pressure. |
| SEC-08 | In a unit/integration harness on audited `main`, invoke wallet mutation with `Integer.MAX_VALUE` followed by `+1`, and attempt a debit larger than the current balance. Persist/reload afterward. | Unchecked addition can wrap or produce a negative balance; the base mutation layer does not enforce `0..Integer.MAX_VALUE`. |
| SEC-09 | Insert three sold marketplace offers priced near `1_000_000_000` for one seller and claim them together. Separately trade multiple configured `CF_*` furniture denominations whose sum exceeds `Integer.MAX_VALUE`. | The aggregate `int` wraps, producing a corrupted/negative/underpaid marketplace or trade credit result. Stock data is unlikely to reach the boundary. |
| SEC-10 | Give one user exactly enough currency for one paid custom badge, then release two authenticated badge-create requests concurrently through a barrier. Repeat near the five-badge cap. | Both requests can observe the same pre-debit balance/count and succeed, overspending or exceeding the quota. Timing is concurrency-dependent. |
| SEC-11 | Use the built-in password or remember endpoint to mint an SSO ticket for a row with `auth_ticket_expires_at = NULL`; authenticate once, wait beyond the intended short login window, and present the exact same ticket again before replacement/logout. | The ticket remains accepted because every validator treats NULL expiry as valid. This requires possession of the exact random bearer ticket; no guessing path was found. |
| SEC-12 | Obtain an access JWT, call logout (and separately change the password), then call an authenticated HTTP endpoint with the old JWT. Repeat with an old remember token after password change. | The access JWT remains valid until `exp`, and old remember state can remain usable because verification is not tied to logout/password state. |
| SEC-13 | Log in, disconnect so the Habbo enters the resume grace window, apply an account/IP/MAC ban, then reconnect with the still-valid SSO ticket before the parked session expires. | The parked Habbo is reattached without the full fresh-login ban path, allowing a narrow resume-time bypass. |
| SEC-14 | Add deterministic fault injection after each marketplace purchase step (offer state update, ownership update, inventory visibility, debit) and execute one purchase per injection point. | Different failure points leave sold-but-undelivered, transferred-but-unpaid, or otherwise divergent durable/in-memory state because no single transaction spans the operation. |
| SEC-15 | Send an authenticated badge request with `Origin: https://attacker.invalid` and inspect response CORS headers. | Audited `main` reflects the arbitrary origin and enables credentials. This is a hardening gap rather than a standalone demonstrated CSRF because the bearer header is not automatically attached by browsers. |
| SEC-17 | Send crafting requests while not in a room, with nonexistent furniture/altar IDs, truncated ingredient arrays, and declared counts larger than remaining packet bytes. | Null dereferences or repeated exceptions/log noise occur on audited `main`; ordinary exceptions are caught by the packet pipeline, so this is not presented as a confirmed process crash. |
| SEC-18 | With a test account satisfying the affected page rank, disable a catalog page and send a direct normal purchase packet for one of its loaded offers rather than requesting page contents first. | The normal purchase route can accept the offer despite disabled page state. Stock affected pages require elevated ranks, limiting ordinary-user reachability. |

## Findings

### SEC-01 — Catalog price arithmetic can still overflow to free purchases

**Severity:** High
**Confidence:** Confirmed code path; exploitation depends on catalog price/offer data
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1064-1072`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1098-1103`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1293-1305`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1398-1420`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemEvent.java:52-54`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/CatalogAdminOfferPayload.java:65-73`

The incoming count is clamped to 100, but the server subsequently performs all of these operations as signed 32-bit `int` values:

```java
amount * item.getAmount()
originalPrice * amount
originalPrice * (amount - totalDiscountedItems)
```

The result is accepted as a price. Affordability and debit are both conditional on `totalCredits > 0`/`totalPoints > 0`. If a positive price multiplication wraps to zero or negative, the order is created but no currency is removed. This is reachable using values accepted by the built-in catalog administration API: a price may be as high as 1,000,000,000, while a purchase can contain up to 100 units.

For a normal one-unit, `haveOffer` catalog entry priced at 1,000,000,000, a client count of 3 passes all quantity/offer checks. With the configured discount batch size of 6, no discount applies, and Java computes `1_000_000_000 * 3` as `-1_294_967_296`. No downstream code makes that value positive or rejects it; both the funds check and debit are skipped.

The separate `amount * item.getAmount()` guard is also calculated as `int`, but the built-in administration limit of 10,000 per offer and packet limit of 100 keep that particular product below overflow. It becomes an overflow route only with direct/imported database values above the administration limit. Independently, a packet count of one does not apply the 100-item check at all, so the built-in administration maximum can create up to 10,000 units in one purchase if such an offer is configured.

**Impact:** free catalog items, large item creation, currency integrity loss, and possible resource exhaustion.

**Fix:** use `long` for all intermediate price/quantity calculations and `Math.multiplyExact`/`Math.addExact`; reject overflow instead of clamping it. Enforce one central maximum logical item count after multiplication. Require every non-free purchase price to be non-negative and within a documented maximum. Revalidate plugin-mutated totals immediately before an atomic debit.

### SEC-02 — Catalog item creation occurs before payment and can persist free/orphaned items

**Severity:** Medium
**Confidence:** Confirmed design flaw; client exploitation requires a specially composed offer and a predictable late failure
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1105-1291`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1293-1310`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/items/ItemManager.java:825-858`

`CatalogManager.purchaseItem` builds the order by calling `ItemManager.createItem`. That method immediately inserts each item with the buyer as owner. Only after all item loops finish does catalog code perform some late checks, fire plugins, and debit currency.

A concrete late-failure shape is a mixed offer containing ordinary furniture followed by guild furniture. `extraData` is client-controlled and is not prevalidated for the guild branch; if ordinary furniture is iterated first, its row is inserted, then invalid guild data returns at `CatalogManager.java:1232-1248` before payment. Music-disc lookup, bot/pet creation, and other exceptions provide additional partial-commit paths. Base items are returned through a `HashSet`, so the exact order is data/JVM dependent rather than guaranteed for every bundle. Rows inserted before the failure remain owned by the user and may appear after inventory reload/relogin.

**Impact:** database-persistent free items, orphaned inventory rows, inconsistent limited-item counters, and duplication through deliberately constructed multi-item offers.

**Fix:** PR #365 validates deterministic order conditions first, acquires the per-user purchase gate atomically, reserves credits and points under one wallet lock, tracks every created item/bot/pet, and deletes those resources plus refunds payment if a later step fails. Limited logs and selected side effects are deferred until delivery succeeds.

### SEC-03 — Gift wrap fee addition can overflow and make gifts free

**Severity:** Medium
**Confidence:** Confirmed arithmetic path; requires non-stock price/configuration values
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java:221-230`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java:493-507`

Gift cost is calculated as an `int` and the configured wrapping fee is added with `+=`. Two positive values can wrap negative. The affordability test then succeeds and the later debit is skipped because it only runs when the total is greater than zero.

Club gift duration also uses `offer.getDays() * 86400` as an `int`, and subscription state is extended before payment. This introduces both timestamp overflow and a partial-commit window.

**Impact:** free gifts and corrupted subscription expiry.

**Fix:** PR #365 uses checked addition/multiplication and bounded duration, reserves the exact gift/subscription payment before delivery, compensates every created gift row on failure, and refunds a failed subscription grant.

### SEC-04 — Earnings reward failure allows replay of already-granted currency

**Severity:** Medium
**Confidence:** Confirmed partial-grant path; the feature is disabled and rewards are zero in the shipped SQL
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/habbohotel/earnings/EarningsCenterManager.java:102-117`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/earnings/EarningsCenterManager.java:367-415`

The claim marker is inserted first. Rewards are then applied sequentially in this order: credits, pixels, points, badge, item, HC days. Currency mutations are in memory and later persisted through normal user saving, while badge/item/HC operations perform separate SQL work.

If a later SQL-backed reward throws—for example, a configured item ID does not exist—the catch block removes the claim marker. Previously granted credits/points are not reverted. The same category can then be claimed again, granting the earlier currencies each time before failing again.

**Impact:** repeatable currency inflation from one claimable reward definition.

**Fix:** PR #369 never removes the unique claim marker after reward delivery begins. A failed suffix therefore remains fail-closed and the already-delivered prefix cannot be claimed repeatedly. A future outbox may improve automatic reconciliation but is not required to close the inflation path.

### SEC-05 — Club-offer totals retain the original signed-integer weakness

**Severity:** High
**Confidence:** Confirmed code path; stock offer values may not reach it
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemEvent.java:154-172`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/ClubOffer.java`
- `Database/Default Database/FullDatabase.sql:1545-1566`

The club/VIP branch loops up to the packet clamp but accumulates `totalDays`, `totalCredits`, and `totalDuckets` in `int` variables. A clamp on iteration count does not make addition safe. Large positive offer values can still wrap totals, bypass affordability logic, reverse a currency mutation, or corrupt subscription time.

The same accepted arithmetic example demonstrates the currency-minting sink: an offer costing 1,000,000,000 credits bought three times produces `totalCredits == -1_294_967_296` while ordinary positive `days` keeps the purchase branch active. `credits < totalCredits` is false, and `giveCredits(-totalCredits)` adds 1,294,967,296 credits. There is no later currency validation. The two shipped offers are far below this threshold; unlike ordinary catalog items, club offers currently require a direct database/import change because no club-offer administration endpoint was identified.

**Impact:** currency gain/loss and subscription-time corruption when high-valued offer data is present.

**Fix:** checked `long` totals, bounds on each loaded offer, reject non-positive/wrapped totals, and atomically debit with subscription extension.

### SEC-06 — One jukebox packet can request an enormous allocation

**Severity:** High
**Confidence:** Confirmed and packet-reachable
**Affected:** `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/JukeBoxRequestTrackDataEvent.java:14-23`

The client controls `count`, which is passed directly to `new ArrayList<>(count)` before the handler proves that the packet contains that many track IDs. A four-byte value near `Integer.MAX_VALUE` can request a multi-gigabyte backing array and raise `OutOfMemoryError`. The 500 KB WebSocket frame limit does not mitigate this because the dangerous count itself is tiny. `OutOfMemoryError` is also not handled by an `Exception` catch.

**Impact:** server process crash or severe memory pressure from a single request.

**Fix:** reject negative values and cap count to a small protocol maximum before allocation. Also require `count <= remainingBytes / 4`, and allocate no more than the accepted cap.

### SEC-08 — Currency storage and mutation are unchecked signed 32-bit operations

**Severity:** Medium (systemic; raises the impact of multiple other findings)
**Confidence:** Confirmed
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/HabboInfo.java:406-422`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/HabboInfo.java:250-275`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/Habbo.java:242-251`
- `Database/Default Database/FullDatabase.sql:30673-30721`

Credits and point currencies use Java `int` and signed SQL `INT`. `addCredits` and points-map `addTo` perform unchecked addition and accept positive or negative deltas. There is no central invariant enforcing `0 <= balance <= maximum`. Any path that aggregates a large reward, plugin mutation, staff action, or concurrent debit can wrap the balance.

**Impact:** negative balances, wrapped balances, accidental minting/burning, and inconsistent affordability decisions.

**Fix:** introduce a single money/currency service with checked `long` arithmetic and atomic `tryDebit`. Store balances as `BIGINT` or enforce a deliberately lower maximum with database constraints. Reject deltas that would leave the valid range. Record an immutable ledger entry for every mutation.

### SEC-09 — Marketplace and trade payout aggregation can overflow

**Severity:** Medium
**Confidence:** Confirmed code path; requires multiple high-value entries/configured denominations
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java:30-34`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java:409-434`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/HabboInventory.java:195-203`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java:270-295`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java:389-398`

Marketplace listings may cost up to 1,000,000,000, but multiple sold prices are summed into an `int`. Three large sales are sufficient to overflow the total shown/paid. Trade credit-furniture values are likewise accumulated into `int` from parsed `CF_*` base-item names.

**Impact:** corrupted, negative, or underpaid payouts. The marketplace path by itself was not shown to mint more than the underlying sales; it primarily destroys or misaccounts value after overflow.

**Fix:** aggregate in checked `long`, cap each denomination and total, and reject rather than wrap. Pay each marketplace row through an idempotent ledger transaction instead of summing first.

### SEC-10 — Financial check-and-debit operations are not atomic across threads

**Severity:** Medium
**Confidence:** Confirmed concurrency design issue; game packets on one channel are ordered, but HTTP/background/room/plugin work can be concurrent
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1019-1026`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1098-1103`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1296-1305`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/custombadge/CustomBadgeManager.java:186-238`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/custombadge/CustomBadgeManager.java:331-357`

The catalog’s `isPurchasingFurniture` flag is a plain field and its check/set is not atomic. Balance reads and later debits are separated by item creation and plugin callbacks. Individual balance methods synchronize access, but the business transaction “check sufficient funds, then debit” is not protected as one operation.

Custom-badge creation has the same issue through concurrent HTTP requests: each request can pass the balance and per-user-count checks before any finishes. This can drive balances negative and exceed the five-badge maximum because no database constraint enforces the limit.

**Impact:** overspending, negative balances, duplicate/excess resources, and inconsistent purchase state.

**Fix:** PR #368 enforces checked exact wallet debits and serializes paid custom-badge count/payment per user. PR #365 atomically gates catalog/gift/club purchases and combines their credit/point debit. These close the demonstrated concurrent request paths without changing external interfaces.

### SEC-11 — Built-in login mints SSO tickets without setting their expiry

**Severity:** Medium (defense in depth; requires disclosure of a valid random bearer ticket)
**Confidence:** Confirmed lifetime behavior; no independent ticket-disclosure or guessing path demonstrated
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java:101-107`
- `Emulator/src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java:246-253`
- `Emulator/src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java:136-149`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java:105-140`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/HabboManager.java:94-149`
- `Database/Database Updates/001_auth_ticket_ttl.sql:1-36`

Both password login and remember-token login update `auth_ticket` but not `auth_ticket_expires_at`. Ticket validation explicitly accepts a `NULL` expiry for backward compatibility, and the schema defaults the column to `NULL`. A ticket minted for a new/built-in account therefore remains reusable until overwritten or logout. Conversely, an account with an old non-NULL expired timestamp may receive a new ticket that is immediately rejected because the old expiry is not refreshed.

Secure login intentionally leaves the ticket reusable, increasing the replay window if it is disclosed.

**Impact:** long-lived session hijacking after ticket disclosure and inconsistent login availability. This is not a direct authentication bypass: an attacker must first obtain the exact random ticket.

**Fix:** PR #371 implements the narrow proportional fix: Polaris's password/remember issuers set a fresh short expiry on the existing column, logout clears it, and unchanged external CMS tickets retain legacy NULL-expiry behavior. No second session store or new external contract is introduced.

### SEC-12 — Access JWTs are not revoked by logout or password change

**Severity:** Medium
**Confidence:** Confirmed
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/networking/gameserver/auth/AccessTokenService.java:40-88`
- `Emulator/src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java:41-84`
- `Emulator/src/main/java/com/eu/habbo/networking/gameserver/auth/AccountChangeEndpoints.java:31-115`

Access tokens contain user ID, issue/expiry times, and type. Verification checks signature, type, and time only. Logout clears SSO/remember state but does not revoke issued JWTs. Password changes likewise leave access tokens, remember-token families, and SSO state active. The default token lifetime is one day.

**Impact:** a stolen access token continues to authorize HTTP APIs after the victim logs out or changes password.

**Fix:** include a server-side session/token version and bind signed access/remember tokens to the current stored password state. Check both during verification so logout can revoke immediately and a password change made by any unchanged CMS invalidates old credentials automatically. Shorten access-token TTL and retain rotating refresh tokens.

### SEC-13 — Ghost-session resume skips fresh account/IP/MAC ban checks

**Severity:** Low
**Confidence:** Confirmed narrow window
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java:105-177`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/gameclients/SessionResumeManager.java:90-140`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/Habbo.java:118-171`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/users/HabboManager.java:94-149`

When a parked/ghost session is resumed, `SecureLoginEvent` attaches the existing `Habbo` and skips the normal load/connect path. Account, IP, and MAC ban checks live in that skipped path. A user banned during the resume grace period can reconnect with the still-valid ticket without a fresh ban decision.

**Impact:** a narrow ban-enforcement race; winning the resume window can leave the already-banned session online beyond the grace period.

**Fix:** revalidate account status and current IP/MAC bans immediately before attaching a resumed session. Explicitly invalidate parked sessions when a ban is created.

### SEC-14 — Marketplace purchase is a multi-step, non-transactional transfer

**Severity:** Medium
**Confidence:** Confirmed integrity risk
**Affected:** `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java:266-340`

The listing state transition, item ownership update, buyer inventory update, and currency debit are separate operations. The conditional listing-state update helps prevent two buyers from purchasing the same offer, but a crash/SQL failure after that update can mark an offer sold without completing delivery, or deliver/change ownership before payment is durably recorded. A trusted installed plugin may also mutate event price after the original affordability check without a second validation; that part is not directly client-controlled in the base emulator.

**Impact:** sold-but-undelivered offers, delivered-but-unpaid items, and accounting disagreement after failures.

**Fix:** PR #368 locks the offer and item rows, revalidates the persisted and plugin-adjusted price, reserves an exact atomic wallet debit, commits offer state and item ownership together, refunds on rollback, and publishes inventory state only after commit.

### SEC-15 — Badge API reflects arbitrary origins with credentials enabled

**Severity:** Low
**Confidence:** Confirmed hardening gap, not a demonstrated current CSRF vulnerability
**Affected:** `Emulator/src/main/java/com/eu/habbo/networking/gameserver/badges/BadgeHttpHandler.java:343-351`

The badge handler reflects any request `Origin`, enables `Access-Control-Allow-Credentials`, and permits `Authorization` plus mutation methods. Other authentication handlers use an origin gate, making this endpoint an inconsistent exception. The endpoint currently authenticates only an explicit bearer header; browsers do not automatically attach such a token cross-origin. Therefore the present code does not establish a standalone CSRF exploit. The policy becomes dangerous if a frontend leaks the token to another origin or authentication later moves to cookies.

**Impact:** unnecessary cross-origin attack surface for authenticated badge mutations.

**Fix:** use the shared `CorsOriginGate`, return no CORS headers for unapproved origins, avoid credentialed CORS unless required, and keep tokens out of script-readable persistent storage.

### SEC-17 — Malformed crafting packets lack basic room/item/count guards

**Severity:** Low
**Confidence:** Confirmed
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/messages/incoming/crafting/RequestCraftingRecipesAvailableEvent.java:17-27`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/crafting/CraftingCraftSecretEvent.java:28-37`

The recipe request dereferences `currentRoom()` and `item.getBaseItem()` without proving that a room/item exists, and trusts an uncapped client count. The secret-craft handler caps count but still dereferences the current room before a null guard. Ordinary exceptions are caught by the packet pipeline, so this is primarily a log-flood and worker-waste issue rather than a demonstrated process crash.

**Impact:** repeated exceptions, noisy logs, and avoidable CPU work from malformed packets.

**Fix:** require an authenticated user in a room, validate a positive item ID, verify item existence/ownership/type, cap every count before looping, and reject packets whose remaining bytes cannot contain the declared entries.

### SEC-18 — Crafted purchases bypass disabled catalog page state

**Severity:** Low
**Confidence:** Confirmed; shipped affected pages require an account that already meets rank 4, 6, or 7
**Affected:**

- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:346-387`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:464-517`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/RequestCatalogPageEvent.java:19-28`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemEvent.java:66-86`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemEvent.java:136-144`
- `Emulator/src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java:164-176`

The manager loads every catalog page and every attached item regardless of page state. Page-content requests enforce `enabled`; the normal purchase handler does not. It checks page rank and rejects the `club_gift` layout, then passes the item to `purchaseItem`. The gift handler also rejects disabled pages, confirming that `enabled` is intended as a server-side availability control.

A client that knows or enumerates a page and catalog-item ID can therefore buy from a disabled page even though requesting that page’s contents is rejected, provided the account still meets the page’s rank. Static parsing found 32 catalog items attached to disabled pages (26 of those are also hidden), but those shipped pages require rank 4, 6, or 7; this is not a stock rank-1 exploit. `visible` alone may intentionally mean “omit from the index but allow direct/featured access,” so this report does not count invisibility alone as a proven authorization bypass. Page-level `club_only` is also omitted in the normal handler; item-level `club_only` still protects items where it is set, but page-only restrictions are not sufficient when such data is configured.

**Impact:** purchase of retired or administratively disabled stock and bypass of page-level availability policy.

**Fix:** centralize a `canAccessPage(habbo, page, requestedType)` decision covering rank, enabled, visible, club status, and catalog mode. Call it from page display, search resolution, normal purchase, gift purchase, and target/recent-purchase routes. Do not treat UI omission as authorization.

## Additional correctness and hardening observations

These are lower priority than the findings above but should be addressed during cleanup:

1. **Registration length mismatch:** HTTP validation permits usernames up to 32 characters while the main schema uses `VARCHAR(25)`. Depending on SQL mode, registration can fail or truncate (`AuthHttpUtil` username pattern, `SessionEndpoints` registration, `FullDatabase.sql` users table).
2. **Email uniqueness is application-only:** registration checks for an existing email and later inserts, but the users table has no unique normalized-email constraint. Concurrent registrations can create duplicates; password-reset lookup uses `LIMIT 1`, making ownership ambiguous.
3. **Rate-limit off-by-one:** `GameMessageRateLimit` checks `count > maximum` before increment, allowing one more request than the nominal maximum.
4. **Unix timestamps remain `int`:** subscription and reward expiry calculations will eventually hit the 2038 boundary, and large duration additions can reach it much earlier.
5. **Async persistence weakens guarantees:** a number of balance/item mutations are queued independently. A clean in-memory result is not proof that all related database writes committed together.

## What appears to be handled well

The review also found several meaningful defenses that should be preserved:

- Normal catalog packet count is clamped to 1–100.
- Game packets are executed in channel order and WebSocket frames have a global size cap.
- Marketplace sale state uses a conditional update, reducing straightforward double-buy races.
- Trading code uses synchronized completion/ownership checks and conditional ownership updates.
- Chest currency deposit/withdraw paths contain quantity caps and synchronization.
- Camera uploads have byte/dimension limits and image validation.
- Prepared statements are used broadly; this review did not identify a clear client-reachable SQL injection path.
- Administrative HTTP/catalog endpoints generally perform permission checks.
- RCON defaults to loopback rather than all interfaces.

These controls reduce exposure but do not provide transactionality or checked arithmetic, which are the two recurring root causes in this report.

## Recommended merge and follow-up order

### Immediate

1. Merge #365 for catalog arithmetic, delivery compensation, purchase concurrency, and page access.
2. Merge #368 for wallet invariants, custom-badge concurrency, aggregation safety, and marketplace transfer consistency.
3. Merge #366 and #369 for packet allocation/crafting guards and earnings replay prevention.
4. Merge #371, #372, and #373 as the narrow independent authentication/session fixes.
5. Merge #370 for the isolated badge-origin policy correction.

### Next release

6. Add live database fault-injection tests for catalog compensation and marketplace rollback.
7. Continue migrating genuinely concurrent financial endpoints to exact wallet debits when a reachable race is demonstrated.
### Defense in depth

8. Add database constraints for non-negative bounded balances, normalized unique email, and quotas that must survive concurrency.
9. Standardize packet guards: maximum count, remaining-byte check, entity existence, ownership, and state/type validation.
10. Move timestamps and durations to 64-bit values where protocol/schema compatibility permits.

## Regression tests to add

- Purchase counts 3 and 100 with prices around each overflow boundary, including the exact `1_000_000_000 * 3` case, discounted offers, points, and gift wrap.
- A catalog bundle where an early furniture component succeeds and a later guild/music-disc component fails; assert no rows and no debit survive.
- Concurrent purchases/custom-badge creates from two executors against one balance; assert at most one succeeds and balance never becomes negative.
- Earnings category with credits plus a failing item reward; retry and assert credits are granted at most once.
- Jukebox counts `-1`, `0`, maximum accepted, maximum + 1, and `Integer.MAX_VALUE`; assert no large allocation.
- Built-in password/remember issuance with NULL and stale expiry values; assert each newly minted built-in ticket receives a fresh deadline while unchanged CMS tickets retain legacy behavior.
- Direct purchase attempts against disabled, hidden, page-level club-only, wrong-mode, and excessive-rank pages; assert every route rejects them.
- Logout/password change followed by use of an old access token; assert rejection.
- Ban an account while its session is parked, then attempt resume; assert rejection and parked-session invalidation.
- Marketplace fault-injection after each transfer step; assert all-or-nothing state.

## Review limitations

This is a source-level audit, not a guarantee that no vulnerabilities remain. A locally provisioned Maven 3.9.11 runtime executed the complete test suite successfully at every listed open remediation head. Withdrawn PR #367 was also tested before closure, but it supplies no remediation coverage. Dependency vulnerability scanners, a live server, production configuration, installed plugins, reverse proxy, CMS, and real network topology were not available. Findings marked configuration-dependent should be checked against deployed data and configuration. Database migration tests, live integration tests, dependency scanning, and packet fuzzing remain recommended before release.
