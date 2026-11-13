# Catalogue bundles that follow existing offers

A bundle (the `single_bundle` layout, or any offer with more than one furni) lists what it sells in
`catalog_items.item_ids`. Until now that list could only name furni (`items_base.id`), so a furni that was already
for sale on its own page had to be set up again for the bundle.

An entry may now name a **catalogue offer** instead:

| Entry | Meaning |
| --- | --- |
| `4501` | one of furni 4501 |
| `4501:2` | two of furni 4501 |
| `@1234` | what catalogue offer 1234 (`catalog_items.id`) sells |
| `@1234:2` | twice what offer 1234 sells |

Entries are separated by `;` and may be mixed: `@1234;@1240:2;4501`.

## What the bundle does

- It hands out the furni of the offers it names, in their amounts. The same furni named twice adds up.
- It **follows** those offers: change an offer's furni or amount and every bundle that names it changes with it. No
  catalogue reload is needed for the bundle itself; it reads the offers when it is shown or bought.
- It keeps **its own** price, club flag, name and page. Only the contents come from the named offers.
- An offer that is named may itself name offers (up to 5 deep).

## Things to know

- A named offer that does not exist, or that leads back to the bundle (a loop), is left out; the gameserver logs
  which. A bundle whose every entry is left out sells nothing, so check the log after setting one up.
- Only the furni of a named offer are taken. Its extra data (a badge code, a pet, a trophy text) is not; a bundle
  uses its own `extradata`.
- Limited (LTD) stock is counted on the bundle's own row, not on the offers it names.
- One furni is capped at 1000 per bundle.

## Example

Offers 1234 (a sofa) and 1240 (a lamp) already exist. A bundle with two lamps, the sofa and a rug that is sold
nowhere else:

```sql
UPDATE catalog_items SET item_ids = '@1234;@1240:2;4501' WHERE id = <bundle offer id>;
```

The catalogue editor in the client accepts the same entries in its "Item IDs" field.
