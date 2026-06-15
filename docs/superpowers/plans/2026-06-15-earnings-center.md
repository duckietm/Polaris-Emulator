# Earnings Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an emulator-owned earnings/rewards hub for the new "Guadagni" UI, with server-side reward definitions and claim protection.

**Architecture:** Add a focused earnings package under `com.eu.habbo.habbohotel.earnings`, wire three incoming handlers and two outgoing composers, and persist claims in a dedicated table with a unique period key. Keep reward definitions config-driven so UI/renderer work can progress independently.

**Tech Stack:** Java 21, Maven, MariaDB SQL updates, existing Arcturus packet manager/composer patterns, JUnit tests.

---

### Task 1: Map Existing Patterns

**Files:**
- Read: `Emulator/src/main/java/com/eu/habbo/messages/PacketManager.java`
- Read: `Emulator/src/main/java/com/eu/habbo/messages/PacketNames.java`
- Read: `Emulator/src/main/java/com/eu/habbo/habbohotel/users/Habbo.java`
- Read: `Emulator/src/main/java/com/eu/habbo/messages/outgoing/MessageComposer.java`

- [ ] Inspect packet registration and composer header lookup.
- [ ] Inspect currency grant methods on `Habbo`.
- [ ] Inspect emulator setting access APIs.
- [ ] Choose the smallest implementation that matches existing style.

### Task 2: Add Earnings Domain

**Files:**
- Create: `Emulator/src/main/java/com/eu/habbo/habbohotel/earnings/EarningsCategory.java`
- Create: `Emulator/src/main/java/com/eu/habbo/habbohotel/earnings/EarningsReward.java`
- Create: `Emulator/src/main/java/com/eu/habbo/habbohotel/earnings/EarningsEntry.java`
- Create: `Emulator/src/main/java/com/eu/habbo/habbohotel/earnings/EarningsClaimResult.java`
- Create: `Emulator/src/main/java/com/eu/habbo/habbohotel/earnings/EarningsCenterManager.java`

- [ ] Define allowlisted categories and client keys.
- [ ] Load enabled flags, cooldowns, and reward values from configuration.
- [ ] Build row state for a user.
- [ ] Implement single claim and claim-all.
- [ ] Grant credits/pixels/points through existing `Habbo` APIs.
- [ ] Grant badges, furni items, and HC days through existing emulator storage paths.
- [ ] Roll back a claim marker if a DB-backed grant fails.

### Task 3: Add Persistence

**Files:**
- Create: `Database Updates/012_earnings_center.sql`

- [ ] Create `users_earnings_claims`.
- [ ] Add unique key on `user_id`, `category`, `period_key`.
- [ ] Keep the migration additive and safe for existing databases.

### Task 4: Add Packet Bridge

**Files:**
- Create: `Emulator/src/main/java/com/eu/habbo/messages/incoming/earnings/RequestEarningsCenterEvent.java`
- Create: `Emulator/src/main/java/com/eu/habbo/messages/incoming/earnings/ClaimEarningsRewardEvent.java`
- Create: `Emulator/src/main/java/com/eu/habbo/messages/incoming/earnings/ClaimAllEarningsRewardsEvent.java`
- Create: `Emulator/src/main/java/com/eu/habbo/messages/outgoing/earnings/EarningsCenterComposer.java`
- Create: `Emulator/src/main/java/com/eu/habbo/messages/outgoing/earnings/EarningsClaimResultComposer.java`
- Modify: packet registration/mapping files discovered in Task 1.

- [ ] Incoming handlers parse only category keys.
- [ ] Outgoing composers serialize rows and claim results.
- [ ] Packet names are documented for renderer alignment.

### Task 5: Test and Build

**Files:**
- Create: `Emulator/src/test/java/com/eu/habbo/habbohotel/earnings/EarningsCenterManagerTest.java`

- [ ] Test disabled feature behavior.
- [ ] Test unknown category rejection.
- [ ] Test single claim success.
- [ ] Test duplicate claim rejection.
- [ ] Test claim-all partial success.
- [ ] Test badge, item, and HC reward serialization state.
- [ ] Test claim rollback after grant failure.
- [ ] Run focused tests.
- [ ] Run `mvn clean package`.

### Task 6: Commit and PR

**Files:**
- Commit all source, test, SQL, spec, and plan files.

- [ ] Commit spec and plan.
- [ ] Commit implementation.
- [ ] Push `feat/earnings-center` to `simoleo89/Arcturus-Morningstar-Extended`.
- [ ] Open ready-for-review PR to `duckietm/Arcturus-Morningstar-Extended:dev`.
