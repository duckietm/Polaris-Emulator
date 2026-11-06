package polaris.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SchemaEvidenceTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void readsCreatedTablesAndAddedColumnsInEveryFormTheMigrationsUse() {
        SchemaEvidence.Migration migration = SchemaEvidence.parse("1", "shapes", """
                -- CREATE TABLE commented_out (id INT);
                /* CREATE TABLE also_commented (id INT); */
                CREATE TABLE plain (id INT NOT NULL PRIMARY KEY);
                CREATE TABLE IF NOT EXISTS `guarded` (id INT);
                ALTER TABLE users ADD COLUMN IF NOT EXISTS `auth_ticket_expires_at` TIMESTAMP NULL;
                ALTER TABLE `rooms`
                    ADD `first` INT NOT NULL DEFAULT 0,
                    ADD COLUMN second INT NOT NULL DEFAULT 0,
                    ADD INDEX idx_rooms_first (first),
                    ADD KEY (second),
                    ADD UNIQUE KEY uq_second (second),
                    ADD CONSTRAINT fk_rooms FOREIGN KEY (first) REFERENCES plain (id);
                SET @ddl = IF(@col_exists = 0,
                    'ALTER TABLE `catalog_items` ADD COLUMN `habbicon_id` INT NOT NULL DEFAULT 0',
                    'SELECT ''already present'' AS info');
                """);

        assertEquals(Set.of("plain", "guarded"), migration.tables());
        assertEquals(
                Set.of(
                        new SchemaEvidence.Column("users", "auth_ticket_expires_at"),
                        new SchemaEvidence.Column("rooms", "first"),
                        new SchemaEvidence.Column("rooms", "second"),
                        new SchemaEvidence.Column("catalog_items", "habbicon_id")),
                migration.columns());
        assertTrue(migration.hasEvidence());
    }

    @Test
    void objectsRemovedWithinTheSameMigrationLeaveNoEvidence() {
        SchemaEvidence.Migration migration = SchemaEvidence.parse("1", "scratch", """
                CREATE TABLE scratch (id INT);
                INSERT INTO target SELECT * FROM scratch;
                DROP TABLE scratch;
                ALTER TABLE target ADD COLUMN tmp INT;
                ALTER TABLE target DROP COLUMN tmp;
                ALTER TABLE target DROP INDEX idx_old;
                ALTER TABLE target DROP PRIMARY KEY;
                """);

        assertFalse(migration.hasEvidence());
        assertEquals(Set.of("scratch"), migration.droppedTables());
        assertEquals(Set.of(new SchemaEvidence.Column("target", "tmp")), migration.droppedColumns());
    }

    @Test
    void objectsALaterMigrationDropsOrRenamesAreNotEvidence() {
        List<SchemaEvidence.Migration> stable = SchemaEvidence.stabilise(List.of(
                SchemaEvidence.parse(
                        "1",
                        "first",
                        "CREATE TABLE keep (id INT); CREATE TABLE gone (id INT); "
                                + "ALTER TABLE keep ADD COLUMN stays INT, ADD COLUMN removed INT;"),
                SchemaEvidence.parse("2", "second", "DROP TABLE IF EXISTS gone; ALTER TABLE keep DROP COLUMN removed;"),
                SchemaEvidence.parse("3", "third", "CREATE TABLE old_name (id INT);"),
                SchemaEvidence.parse("4", "fourth", "RENAME TABLE old_name TO new_name;")));

        assertEquals(Set.of("keep"), stable.get(0).tables());
        assertEquals(
                Set.of(new SchemaEvidence.Column("keep", "stays")),
                stable.get(0).columns());
        assertFalse(stable.get(2).hasEvidence(), "a table renamed later cannot prove the migration that created it");
    }

    @Test
    void dataOnlyMigrationsCarryNoEvidence() {
        SchemaEvidence.Migration migration = SchemaEvidence.parse("1", "data", """
                INSERT IGNORE INTO emulator_settings (`key`, `value`) VALUES ('a', 'b');
                UPDATE users SET motto = 'ADD COLUMN is just text here';
                """);

        assertFalse(migration.hasEvidence());
        assertTrue(migration.columns().isEmpty());
    }

    @Test
    void appliedThroughIsTheLongestPresentPrefixAndLaterPresenceIsReportedSeparately() {
        SchemaEvidence.Assessment assessment = new SchemaEvidence.Assessment("3", new java.util.LinkedHashMap<>() {
            {
                put("1", SchemaEvidence.Presence.PRESENT);
                put("2", SchemaEvidence.Presence.NO_EVIDENCE);
                put("3", SchemaEvidence.Presence.PRESENT);
                put("4", SchemaEvidence.Presence.ABSENT);
                put("5", SchemaEvidence.Presence.PRESENT);
            }
        });

        assertEquals(List.of("1", "2", "3"), assessment.evidentlyApplied());
        assertEquals(List.of("5"), assessment.presentBeyond());

        SchemaEvidence.Assessment nothing =
                new SchemaEvidence.Assessment(null, java.util.Map.of("1", SchemaEvidence.Presence.ABSENT));
        assertNull(nothing.appliedThrough());
        assertTrue(nothing.evidentlyApplied().isEmpty());
    }

    @Test
    void everyPackagedMigrationParsesAndTheChainCarriesEnoughEvidence() throws IOException {
        List<SchemaEvidence.Migration> packaged;
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            packaged = files.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(path -> {
                        String name = path.getFileName().toString();
                        String version = name.substring(1, name.indexOf("__"));
                        try {
                            return SchemaEvidence.parse(version, name, Files.readString(path, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
        List<SchemaEvidence.Migration> stable = SchemaEvidence.stabilise(packaged);

        long withEvidence =
                stable.stream().filter(SchemaEvidence.Migration::hasEvidence).count();
        assertTrue(
                withEvidence >= 40,
                "expected most schema migrations to leave checkable objects, found " + withEvidence);

        SchemaEvidence.Migration baseline = stable.get(0);
        assertEquals(MigrationRunner.BASELINE_VERSION, baseline.version());
        assertTrue(baseline.tables().contains("users"));
        assertTrue(baseline.tables().contains("catalog_pages"));

        // The catalog draft tables are rebuilt by a later migration, so they must not count as
        // evidence for the migration that first created them.
        SchemaEvidence.Migration drafts = stable.stream()
                .filter(migration -> migration.version().equals("20260802090000"))
                .findFirst()
                .orElseThrow();
        assertFalse(drafts.tables().contains("catalog_versions"));
        assertTrue(drafts.tables().contains("catalog_id_sequences"));

        // Guarded ADD COLUMNs inside PREPARE strings must still count as evidence.
        SchemaEvidence.Migration achievements = stable.stream()
                .filter(migration -> migration.version().equals("20260911160000"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Set.of(
                        new SchemaEvidence.Column("achievements", "state"),
                        new SchemaEvidence.Column("achievements", "display_method"),
                        new SchemaEvidence.Column("achievements", "subcategory")),
                achievements.columns());

        SchemaEvidence.Migration talents = stable.stream()
                .filter(migration -> migration.version().equals("20260911160100"))
                .findFirst()
                .orElseThrow();
        assertEquals(Set.of(new SchemaEvidence.Column("achievements_talents", "reward_hc_days")), talents.columns());

        SchemaEvidence.Migration habbicons = stable.stream()
                .filter(migration -> migration.version().equals("20260911150000"))
                .findFirst()
                .orElseThrow();
        assertEquals(Set.of("habbicon_collections", "habbicons", "users_habbicons"), habbicons.tables());
        assertEquals(Set.of(new SchemaEvidence.Column("catalog_items", "habbicon_id")), habbicons.columns());

        // The reward track texts table is the last object a migration leaves behind so far; the
        // index clean-up after it drops nothing checkable and counts as no evidence, which is
        // what the adoption and reconcile tests build their pending sets on.
        SchemaEvidence.Migration rewardTexts = stable.stream()
                .filter(migration -> migration.version().equals("20260919150000"))
                .findFirst()
                .orElseThrow();
        assertEquals(Set.of("reward_track_texts"), rewardTexts.tables());
        SchemaEvidence.Migration duplicateIndexes = stable.stream()
                .filter(migration -> migration.version().equals("20260919170000"))
                .findFirst()
                .orElseThrow();
        assertFalse(duplicateIndexes.hasEvidence());
        assertTrue(duplicateIndexes.droppedColumns().isEmpty());
    }
}
