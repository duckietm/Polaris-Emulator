# Earnings Center Design

## Goal

Add an emulator-owned rewards hub for the "Guadagni" UI. The client and renderer may decide how it looks, but the emulator must own reward amounts, claim eligibility, cooldowns, and anti-abuse checks.

## Scope

The first emulator version exposes ten earnings categories:

- `daily_gift`
- `games`
- `achievements`
- `marketplace`
- `hc_payday`
- `level_progress`
- `donations`
- `bonus_bag`
- `mystery_boxes`
- `club_job`

Every category can be enabled, disabled, configured with one or more reward currencies, and claimed through a single-row claim or a claim-all request. Categories that are not yet backed by a native hotel subsystem still work through static configuration, so the UI contract is stable while deeper integrations are added later.

Supported configured reward types are credits, pixels/duckets, seasonal points, badges, furni items, and HC days.

## Architecture

Add a focused `com.eu.habbo.habbohotel.earnings` package:

- `EarningsCenterManager` loads category definitions from emulator settings, builds per-user state, and performs claims.
- `EarningsCategory` is the allowlisted category enum and carries the client key.
- `EarningsReward` represents one configured reward.
- `EarningsEntry` is the serializable row state sent to the client.
- `EarningsClaimResult` reports single/all claim outcomes.

The packet layer only parses category keys and delegates to the manager. The client never sends amounts, cooldowns, or reward definitions.

## Persistence

Add a database update that creates `users_earnings_claims`:

- `id`
- `user_id`
- `category`
- `period_key`
- `claimed_at`
- unique key on `user_id`, `category`, `period_key`

The unique key is the main double-claim guard. `period_key` is calculated by the emulator from the category cooldown. Daily-style rewards use the UTC date key by default. One-time or long cooldown rows can use the cooldown bucket derived from `claimed_at`.

## Configuration

Add emulator settings with safe defaults:

- `earnings.enabled=0`
- `earnings.<category>.enabled=1`
- `earnings.<category>.cooldown.seconds=86400`
- `earnings.<category>.credits=0`
- `earnings.<category>.pixels=0`
- `earnings.<category>.points=0`
- `earnings.<category>.points.type=5`
- `earnings.<category>.badge=`
- `earnings.<category>.item_id=0`
- `earnings.<category>.item.quantity=1`
- `earnings.<category>.hc.days=0`
- `earnings.<category>.native.enabled=0/1`

The feature defaults off so existing hotels do not receive surprise economy changes after deploying the jar.
Marketplace and HC payday default to native integrations once the feature is enabled, because both already have server-side claim ledgers.
Achievements and level progress use native eligibility by default: achievement score buckets and talent-track levels decide when the configured reward may be claimed.

## Packet Contract

Add three incoming handlers:

- `RequestEarningsCenterEvent`
- `ClaimEarningsRewardEvent`
- `ClaimAllEarningsRewardsEvent`

Add two outgoing composers:

- `EarningsCenterComposer`
- `EarningsClaimResultComposer`

Composer format is intentionally simple and renderer-friendly: category key, enabled state, claimable state, next claim timestamp, rewards, and result code. Header IDs must be wired through `messages.ini`/packet registration in the same style as the rest of the emulator. If the renderer side chooses final IDs later, only the packet mapping should need adjustment.

## Security

- Reject unknown category keys.
- Reject all claims when `earnings.enabled=0`.
- Never trust reward amounts from the client.
- Clamp configured rewards to non-negative values and bounded item/HC limits.
- Roll back the claim record if a DB-backed reward grant fails.
- Use the database unique key to prevent concurrent double claims.
- `claim all` processes only claimable rows and returns per-category results.
- Marketplace claims use the existing marketplace sold-offer payout path.
- HC payday claims use existing unclaimed `logs_hc_payday` rows.
- Achievement claims can be scoped to score buckets via `earnings.achievements.score.step`.
- Level progress claims can be scoped to the current highest citizenship/helper level.

## Tests

Add unit tests around the manager-level logic:

- disabled global feature returns disabled rows and rejects claims
- unknown category is rejected
- successful claim grants configured currency once
- duplicate claim in the same period is rejected
- claim-all grants all claimable rows and skips already claimed rows
- badge/item/HC reward rows are included in state
- failed reward grants roll back the claim record

Packet tests can remain light because renderer IDs may be finalized separately; the critical behavior is the server-side claim guard.
