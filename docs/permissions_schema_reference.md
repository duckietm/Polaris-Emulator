# Permissions schema reference

## Overview

The legacy `permissions` table stores:

- one row per rank
- one column per permission key

That works for runtime, but it becomes very hard to read and maintain once the number of permission keys grows.

The updated design keeps only the rank metadata separated, while the permission matrix itself becomes one readable table:

- `permission_ranks`
  - one row per rank
  - stores rank metadata such as `rank_name`, `badge`, `level`, `prefix`, `room_effect`, and the automatic currency amounts
- `permission_definitions`
  - one row per permission key
  - stores the permission comment in the same row
  - stores one column per rank using the format `rank_<id>`

Example:

| permission_key | max_value | comment | rank_1 | rank_2 | rank_7 |
| --- | --- | --- | --- | --- | --- |
| `acc_ads_background` | `1` | Allows editing room advertisement backgrounds. | `0` | `0` | `1` |

## Runtime behavior

- The emulator still supports the legacy `permissions` table as a fallback.
- If `permission_ranks` and `permission_definitions` exist and contain data, the emulator loads the new schema instead.
- If the new schema is missing, incomplete, or fails to load, the emulator falls back to the legacy `permissions` table automatically.

Relevant runtime files:

- `Arcturus-Morningstar-Extended/Emulator/src/main/java/polaris/hotel/permissions/PermissionsManager.java:45`
- `Arcturus-Morningstar-Extended/Emulator/src/main/java/polaris/hotel/permissions/Rank.java:71`
- `Arcturus-Morningstar-Extended/Emulator/src/main/java/polaris/hotel/modtool/ModToolManager.java:57`

## Tables

### `permission_ranks`

This table stores only rank metadata:

- `id`
- `rank_name`
- `hidden_rank`
- `badge`
- `job_description`
- `staff_color`
- `staff_background`
- `level`
- `room_effect`
- `log_commands`
- `prefix`
- `prefix_color`
- `auto_credits_amount`
- `auto_pixels_amount`
- `auto_gotw_amount`
- `auto_points_amount`

#### `permission_ranks` field meanings

- `id`
  - Numeric rank id used everywhere else in the emulator, including `users.rank` and the dynamic `rank_<id>` columns in `permission_definitions`.
- `rank_name`
  - Human-readable name of the rank, such as `User`, `Moderator`, or `Administrator`.
- `hidden_rank`
  - When enabled, the rank is treated as hidden in places where staff visibility should be reduced.
- `badge`
  - Badge code automatically associated with the rank.
- `job_description`
  - Staff/job description text shown in features that expose rank profile details.
- `staff_color`
  - Hex color used by staff UI or visuals that depend on the rank color.
- `staff_background`
  - Background asset name used for staff visuals tied to the rank.
- `level`
  - Priority/order value of the rank; higher values usually mean stronger staff level or broader access.
- `room_effect`
  - Default avatar effect id associated with the rank when that feature is used.
- `log_commands`
  - Controls whether commands executed by users with this rank should be logged in command logs.
- `prefix`
  - Short in-room staff prefix/tag associated with the rank.
- `prefix_color`
  - Hex color used for the displayed rank prefix.
- `auto_credits_amount`
  - Automatic credit amount granted by rank-based reward/payday style logic, if used by the hotel.
- `auto_pixels_amount`
  - Automatic duckets/pixels amount granted by rank-based reward/payday style logic, if used by the hotel.
- `auto_gotw_amount`
  - Automatic GOTW-style points amount granted by rank-based reward/payday style logic, if used by the hotel.
- `auto_points_amount`
  - Automatic activity-points amount granted by rank-based reward/payday style logic, if used by the hotel.

### `permission_definitions`

This table stores:

- `permission_key`
- `max_value`
- `comment`
- one dynamic column per rank:
  - `rank_1`
  - `rank_2`
  - `rank_3`
  - ...

That means the table itself is already the readable matrix you wanted:

- rows = permission keys
- columns = rank values
- comment stays next to the key

## Value semantics

Permission values keep the same meaning as today:

- `0` = disabled
- `1` = allowed
- `2` = allowed only when room-owner rights may be used

The schema stores that information in:

- `permission_definitions.max_value`

## Migration behavior

`Database Updates/004_normalize_permissions_schema.sql` does the following:

1. keeps the legacy `permissions` table untouched
2. creates `permission_ranks`
3. creates `permission_definitions`
4. copies rank metadata from `permissions` into `permission_ranks`
5. creates any missing `rank_<id>` columns in `permission_definitions`
6. creates one row per permission key with `max_value` and a comment
7. applies curated per-key comments so every permission explains what it actually does in code
8. copies each old permission value into the proper `rank_<id>` column

It also removes the older experimental objects if they already exist:

- `permission_rank_values`
- `permission_nodes`
- `permissions_matrix_view`
- `refresh_permissions_matrix_view`

## Adding a new rank later

When you add a new rank after the migration:

1. insert the rank metadata into `permission_ranks`
2. reload permissions with emulator restart or `:update_permissions`
3. the emulator automatically creates the missing `rank_<id>` column in `permission_definitions` if it does not exist yet
4. set the new `rank_<id>` values in `permission_definitions`

You can still run the helper procedure manually if you want to sync the schema before the next reload:

```sql
CALL refresh_permission_definition_rank_columns();
```

If you want to refresh all values again from the old legacy table during rollout, you can also run:

```sql
CALL refresh_permission_definition_values();
```

## Notes about comments and legacy keys

The comments stored in `permission_definitions.comment` are intentionally hand-curated.

- Where a Java handler exists, the comment follows the real runtime behavior.
- Where only legacy command texts exist, the comment marks the key as legacy and explains the intended behavior from those texts.
- Where a key is still present for compatibility but no direct handler is found in the current tree, the comment says so explicitly.

The new schema intentionally preserves legacy and inconsistent permission keys so current functionality stays intact.

Examples:

- `cmd_word_quiz`
- `cmd_wordquiz`
- `cms_dance`
- `kiss_cmd`

Those can be cleaned up later only after runtime behavior has been verified and the hotel no longer depends on the old names.
