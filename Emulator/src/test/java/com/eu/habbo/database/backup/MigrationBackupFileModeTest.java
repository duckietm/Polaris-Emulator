package com.eu.habbo.database.backup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The partial dump - password hashes and e-mails included - was written with the default permissions and restricted
 * only once complete, readable by any local user while the dump ran.
 */
class MigrationBackupFileModeTest {

    @Test
    void thePartialDumpIsRestrictedBeforeItIsWritten() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/database/backup/MariaDbMigrationBackup.java"));
        int compress = source.indexOf("private static long compress(");
        String body = source.substring(compress, source.indexOf("return total;", compress));

        int created = body.indexOf("Files.createFile(target);");
        int hardened = body.indexOf("harden(target);");
        int opened = body.indexOf("Files.newOutputStream(target");

        assertTrue(created > 0 && hardened > created && opened > hardened, body);
    }
}
