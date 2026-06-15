# Earnings Center Packet Contract

This document is the emulator-side contract for the "Guadagni" UI.

## Incoming

### `RequestEarningsCenterEvent`

- Header: `9308`
- Body: empty
- Response: `EarningsCenterComposer`

### `ClaimEarningsRewardEvent`

- Header: `9309`
- Body:
  - `String categoryKey`
- Response: `EarningsClaimResultComposer`

### `ClaimAllEarningsRewardsEvent`

- Header: `9310`
- Body: empty
- Response: `EarningsClaimResultComposer`

## Outgoing

### `EarningsCenterComposer`

- Header: `9407`
- Body:
  - `int entryCount`
  - repeated entry:
    - `String categoryKey`
    - `boolean enabled`
    - `boolean claimable`
    - `int nextClaimAt`
    - `int rewardCount`
    - repeated reward:
      - `String type`
      - `int amount`
      - `int pointsType`
      - `String data`

### `EarningsClaimResultComposer`

- Header: `9408`
- Body:
  - `int resultCount`
  - repeated result:
    - `String categoryKey`
    - `String status`
    - `boolean success`
    - `boolean hasEntry`
    - entry body when `hasEntry=true`, same shape as `EarningsCenterComposer`

## Categories

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

## Reward Types

- `credits`
- `pixels`
- `points`
- `badge`
- `item`
- `hc_days`

For `points`, `pointsType` carries the currency type. For `badge`, `data` carries the badge code. For `item`, `data` carries the `items_base.id`. Other reward types keep `data` empty.

`marketplace` and `hc_payday` can be native rows. In native mode the amounts come from existing server state:

- `marketplace`: sold marketplace offers waiting for payout
- `hc_payday`: unclaimed rows in `logs_hc_payday`
- `achievements`: configured rewards gated by achievement score buckets
- `level_progress`: configured rewards gated by citizenship/helper talent level

## Result Status

- `success`
- `disabled`
- `unknown_category`
- `already_claimed`
- `no_reward`
- `error`

The client must not send reward amounts. Claim eligibility and rewards are always server authoritative.
