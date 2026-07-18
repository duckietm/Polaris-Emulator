# Canonical Furni Import and Verification Pipeline

This is the single acceptance procedure for furni changes shared by Polaris, Nitro Render, UI, and the live `Nitro-Files` tree. An import is not complete when only the database row exists.

## Required import package

Every imported furni must provide, as one reviewed package:

- the `items_base` row and every intended `catalog_items` row;
- one `FurnitureData.json` entry whose `classname`, numeric `id`, and floor/wall section match `item_name`, `sprite_id`, and `type`;
- `<classname>.nitro` in `nitro-assets/bundled/furniture`;
- `<classname>_icon.png` in `swf/dcr/hof_furni/icons`;
- `<classname>.swf` when the target deployment still serves legacy SWF assets;
- for redeemable names (`CF_`, `CFC_`, `PF_`, `DF_`, and `CF_diamond_`), bundle `logicType=furniture_credit` and `logic.credits` equal to the value encoded in the classname.

## Procedure

1. Stage the SQL rows, `FurnitureData.json`, bundle, icon, and optional SWF together. Do not write only one layer to production.
2. Apply the SQL in one MariaDB transaction on a disposable or staging schema first.
3. Point the validator at the staged/exported `items_base` and the exact `Nitro-Files` tree that the client will read.
4. Promote the package only when the validator exits with code `0`.
5. Archive the generated JSON report with the import. Exit code `1` means inconsistencies were found; exit code `2` means the audit itself could not run.
6. If promotion fails after files were copied, restore the prior `FurnitureData.json` and asset files, then roll back or reverse the database transaction. Never accept a database-only or files-only import.

## Windows command

From the emulator repository:

```powershell
.\scripts\verify-furni-import.ps1 `
  -ItemsSource '.\Database\Default Database\FullDatabase.sql' `
  -FurnitureData '..\Nitro-Files\nitro-assets\gamedata\FurnitureData.json' `
  -NitroRoot '..\Nitro-Files' `
  -Report '.\reports\furni-consistency.json'
```

Pass `-ItemsJson` when `ItemsSource` is a JSON array/export instead of a MariaDB dump. Pass `-RequireSwf` only for deployments that require legacy SWFs.

## Direct database command

The Java CLI can audit the current database without exporting credentials to a file:

```powershell
$env:POLARIS_DB_PASSWORD = '<local secret>'
$jar = Get-ChildItem '.\Emulator\target\*-jar-with-dependencies.jar' |
  Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
java -cp $jar.FullName `
  com.eu.habbo.tools.furni.FurniConsistencyCli `
  --jdbc-url 'jdbc:mariadb://127.0.0.1:3306/habbo' `
  --db-user 'root' `
  --db-password-env 'POLARIS_DB_PASSWORD' `
  --furniture-data '..\Nitro-Files\nitro-assets\gamedata\FurnitureData.json' `
  --bundles '..\Nitro-Files\nitro-assets\bundled\furniture' `
  --icons '..\Nitro-Files\swf\dcr\hof_furni\icons' `
  --report '.\reports\furni-consistency-live.json'
```

The CLI never prints the password and performs no database or asset mutation.
