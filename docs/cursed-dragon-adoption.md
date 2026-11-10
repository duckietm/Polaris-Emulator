# Cursed dragon adoption

The official client's adoption window: a player picks the blue or the purple dragon and gets it once, for free.
The window opens on the client link `cursed_dragon_adoption/open`.

## 1. What each dragon gives

Each choice gives a furni. Migration `V20261110130000__cursed_dragon_packages.sql` sets the first choice (0, the blue
dragon) to `diamond_dragon` and the second (1, the purple dragon) to `rare_dragonlamp*9`, looked up by item name. It
only does so when the claim has no choices yet; a hotel that set its own keeps them.

To set them yourself, or change them later (`item_id` is an `items_base` id):

```sql
DELETE FROM special_items_free_claim_choices WHERE claim_id = 'cursed_dragon_adoption';

INSERT INTO special_items_free_claim_choices (claim_id, choice, item_id)
SELECT 'cursed_dragon_adoption', 0, MIN(id) FROM items_base WHERE item_name = 'diamond_dragon'
UNION ALL
SELECT 'cursed_dragon_adoption', 1, MIN(id) FROM items_base WHERE item_name = 'rare_dragonlamp*9';
```

**The dragon itself.** A choice can also give a pet, straight into the pets inventory. Migration
`V20261110140000__special_items_claim_choice_pets.sql` adds three columns to the choices and sets the adoption's two
choices to the blue and the purple cursed dragon, whatever furni they give:

| Column | Meaning |
|---|---|
| `pet_type` | The pet (`pet_actions.pet_type`); -1 gives no pet. |
| `pet_breed` | Its colour: the palette (0 blue, 1 purple, 2 green for the cursed dragon). |
| `pet_name` | The name the pet gets. |

A choice with `item_id` 0 and a pet gives only the pet. To set the pets yourself:

```sql
UPDATE special_items_free_claim_choices SET pet_type = 37, pet_breed = 0, pet_name = 'Blue Dragon'
WHERE claim_id = 'cursed_dragon_adoption' AND choice = 0;
UPDATE special_items_free_claim_choices SET pet_type = 37, pet_breed = 1, pet_name = 'Purple Dragon'
WHERE claim_id = 'cursed_dragon_adoption' AND choice = 1;
```

**The badge.** A choice can also give a badge (`badge_code`). Migration
`V20261110150000__special_items_claim_choice_badges.sql` sets the official ones: `PET17` ("I adopted a Blue Cursed
Dragon!") for the blue dragon and `PET18` ("I adopted a Purple Cursed Dragon!") for the purple one. Their names are
in the texts (`badge_name_PET17`, `badge_desc_PET17`, and the same for `PET18`); the badge images `PET17.gif` and
`PET18.gif` go in the badge folder of the assets (`album1584`). A player who already has the badge keeps theirs.

The official crates (`drago_cursed_1_package` blue, `drago_cursed_2_package` purple) are not added to `items_base`.
A hotel that adds them itself can use them as rewards: opening one gives pet 37 in its colour, like a cow box.

The choices alone offer the claim. A row in `special_items_free_claims` is only needed to close the adoption
without deleting them:

```sql
INSERT INTO special_items_free_claims (claim_id, item_id, enabled)
VALUES ('cursed_dragon_adoption', 0, 0)
ON DUPLICATE KEY UPDATE enabled = 0;   -- 1 opens it again
```

Who adopted what is in `users_special_item_claims`. To let players who adopted adopt again:

```sql
DELETE FROM users_special_item_claims WHERE claim_id = 'cursed_dragon_adoption';
```

Both buttons say "Adopted" for everyone although nobody adopted: the claim is not offered. There are no choices for
the claim id, or its row in `special_items_free_claims` has `enabled` 0. The emulator logs this once per claim id
("Free claim ... is not offered").

## 2. A catalogue page that opens the window

An `info_loyalty` page shows its `page_text1` as text with links; a link whose address starts with `event:` runs a
client link. This adds a top-level page (set `parent_id` to another page's id to put it under that page):

```sql
INSERT INTO catalog_pages
    (parent_id, caption_save, caption, page_layout, icon_color, icon_image, min_rank, order_num,
     visible, enabled, club_only, catalog_mode, vip_only, page_headline, page_teaser, page_special,
     page_text1, page_text2, page_text_details, page_text_teaser, room_id, includes)
VALUES
    (-1, 'cursed_dragon_adoption', 'Dragon Adoption', 'info_loyalty', 1, 42, 1, 50,
     '1', '1', '0', 'NORMAL', '0', '', '', '',
     '<b><font color="#0e3f52" size="16">Two dragons need a home</font></b>\r\n\r\nDeep in the cursed caves two dragon crates were found. You can adopt one of the dragons, for free, but only one.\r\n\r\n<a href="event:cursed_dragon_adoption/open">Click here to choose your dragon</a>',
     '', '', '', 0, '');
```

Then reload the catalogue (`:update_catalog`, or the All-in-1 Manager's reload).

The same link works anywhere the client runs links: a news item, a hotel view button, a targeted offer text.

### The button on the home screen

The official hotel showed a "free cursed dragon pet" button on the left of the home screen. Octane shows one by
itself: on every visit of the home screen it asks the server whether this user can still adopt, and shows the
button only then. A hotel that does not offer the adoption never shows it, and it is gone for a user once they
adopted. Clicking it opens the adoption window.

Its two lines are the texts `cursed_dragon_adoption.button.title` and `cursed_dragon_adoption.button.info`.
`cursed_dragon_adoption.button.enabled` (`ui-config`, default `true`) switches it off, for a hotel that links to the
window from its own hotel view slot instead.

## 3. Client settings (`ui-config`)

| Key | Default | Meaning |
|---|---|---|
| `cursed_dragon_adoption.claim_id` | `cursed_dragon_adoption` | The claim id of step 1. |
| `cursed_dragon_adoption.pet_type` | `37` | The pet drawn on the crates, palette 0 (blue) and 1 (purple). |
| `cursed_dragon_adoption.button.enabled` | `true` | The home screen button, shown while the user can still adopt. |

The texts are the `cursed_dragon_adoption.*` keys (`title`, `eyebrow`, `intro.title`, `intro.body`, `choose`,
`blue`, `purple`, `adopt`, `adopted`, `confirm.title`, `confirm.desc` with `%colour%`, `blue.name`,
`purple.name`); without them the window shows English.

## 4. The dragon on the crates (pet type 37)

The window draws pet type 37, the cursed dragon, on each crate: palette 0 blue, 1 purple (the pet also has a green
one, 2). Two parts come with the code, one is yours to place.

- **Database** (comes with Polaris): migration `V20261110120000__cursed_dragon_pet.sql` adds the pet, its three
  colours and the dragon's commands. It leaves a pet 37 you added yourself alone.
- **Client pet list** (comes with Octane): `pets.json` and its example list id 37 as `curseddragon`. A hotel with
  its own `pets.json` adds the line itself, after 36:

  ```json
  { "id": 37, "lib": "curseddragon", "name": "Cursed Dragon", "description": "" }
  ```

- **Graphics** (yours to place): `curseddragon.hab` goes in the `pets` folder of the assets, next to
  `dragon.hab` (`pet.asset.url`). Without it the crates show without a dragon.

The pet's names in the texts are `pet.type.37` and `pet.breed.37.0` to `pet.breed.37.2`.
