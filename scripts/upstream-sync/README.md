# Syncing with duckietm/Polaris-Emulator

This tree renamed the emulator (`com.eu.habbo` -> `polaris`, `habbohotel` -> `hotel`,
`Habbo` -> `User`, …). Upstream keeps the old names, so its commits cannot be merged as they
are. `sync.sh` ports them instead: every file upstream changed since `BASE` is exported as it
was and as it is now, both copies go through the same rename chain this tree went through,
and the result is merged three-way into the current file.

```
scripts/upstream-sync/sync.sh            # port, format, compile, run the gates
./Emulator/mvnw -f Emulator/pom.xml -B test   # the full suite
git add -A && git commit                 # review first
scripts/upstream-sync/sync.sh --done     # BASE becomes the commit just ported; commit it
scripts/upstream-sync/sync-renderer.sh ../renderer-polaris   # then the renderer
```

`sync.sh` stops with exit 3 on a conflict it cannot resolve (import order and formatting it
resolves itself), 4 when the tree does not compile, 5 when a gate fails. It never commits.
The gates are the plugin ABI gate, the old-name plugin fixtures in the assembled jar, the
packet contract tests, the wired contracts, the frozen architecture baseline and every test
class the sync added.

`sync-renderer.sh` merges `Dev` into the renderer as it is (the renderer is not renamed) and
sets its copy of `protocol/packet-field-contracts.json` to this repository's, which is the
one conflict to expect there.

## The maps

| File | What it renames |
| --- | --- |
| `maps/types.tsv` | classes (`Habbo` -> `User`, `HabboItem` -> `UserItem`, nested types, …) |
| `maps/test-types.tsv` | test classes named after renamed classes |
| `maps/methods.tsv` | methods (`getHabbo` -> `getUser`, private ones included) |
| `maps/constants.tsv` | constants (`HABBO_NOT_FOUND` -> `USER_NOT_FOUND`, …) |
| `maps/identifiers.tsv` | variables, parameters and private fields (`habbo` -> `user`, …) |
| `maps/composers.tsv` | the composers named after the renderer's parsers |
| `maps/field-owners.txt` | the classes whose public `habbo` fields became `user` |
| `maps/moves.tsv` | files that moved to another folder after the rename |

Packages are renamed by rules inside `sync.sh` and `rename.pl`.

**When you rename or move a file in this tree, add its line here** (and the matching line in
`Emulator/src/main/resources/polaris/plugin/legacy-names.txt` when the class is public), or
the next sync looks for the file in its old place and reports it `MISSING`.

Strings are never renamed: user-visible texts, SQL (`habbo_id`, `habbo_mentions`), config keys
and the stored `"HABBO_CLUB"` value keep their words.
