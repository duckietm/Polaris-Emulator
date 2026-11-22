# Build height

The build height widget (the toolbar button in a room) lets a player place or move furniture at a
height they pick instead of on top of what is already on the square. It is part of Polaris; the
old BuildHeight plugin is not needed and must not be installed alongside it.

## Who gets the widget

A player gets it in a room when both hold:

- they may build there (owner, room rights, or group rights), and
- their rank has the `cmd_buildheight` permission.

Every rank is granted `cmd_buildheight` when the permission is first created. To keep the widget for
staff only, set the lower ranks to `0` in `permission_definitions`:

```sql
UPDATE permission_definitions SET rank_1 = 0, rank_2 = 0 WHERE permission_key = 'cmd_buildheight';
```

A hotel that ran the plugin keeps the rank values it had.

Playtest mode hides the widget, as before.

## The height range

| `emulator_settings` key | Default | What |
|---|---|---|
| `building.cmd_buildheight.min_height` | `0` | Lowest height, in tiles from the floor of the square. A negative value allows building below the floor. |
| `building.cmd_buildheight.max_height` | `40` | Highest height, in tiles. |

The range never leaves what furniture can stand at: the top is at most the furniture ceiling (40)
and the bottom at most that far below the floor. The client's slider follows the range the server
sends, and the server keeps a height from the client inside it.

These are the names the plugin used, so a hotel that ran it (default `-40` to `40`) keeps its range.
