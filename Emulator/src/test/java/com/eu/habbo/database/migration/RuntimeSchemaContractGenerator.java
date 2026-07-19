package com.eu.habbo.database.migration;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Update-only Maven entry point for the generated runtime schema contract.
 *
 * <p>The class deliberately does not match the normal Surefire or Failsafe
 * naming patterns. It runs only through the update-runtime-schema-contract
 * profile, while MigrationRunnerIT keeps ordinary verification check-only.
 */
final class RuntimeSchemaContractGenerator {

    private static final String OUTPUT_PROPERTY = "runtimeSchemaContract.output";

    @Test
    void regenerateFromFullyMigratedMariaDb() throws Exception {
        Path output = outputPath();
        try (HikariDataSource dataSource =
                     TestDatabase.freshDatabase("runtime_schema_contract_generator")) {
            // Contract generation must be able to represent an intentional table
            // removal, so apply Flyway directly before validating the old contract.
            MigrationRunner.flyway(dataSource).migrate();
            String generated = RuntimeSchemaValidator.generateContract(dataSource);
            updateAtomically(output, generated);
            assertEquals(generated, Files.readString(output, StandardCharsets.UTF_8));
        }
    }

    private static Path outputPath() {
        String configured = System.getProperty(OUTPUT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "Run this generator through -Pupdate-runtime-schema-contract");
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static void updateAtomically(Path output, String generated) throws IOException {
        if (Files.exists(output)
                && generated.equals(Files.readString(output, StandardCharsets.UTF_8))) {
            System.out.println("Runtime schema contract is already current: " + output);
            return;
        }

        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(
                output.getParent(),
                output.getFileName().toString(),
                ".tmp");
        try {
            Files.writeString(temporary, generated, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        output,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        System.out.println("Updated runtime schema contract: " + output);
    }
}
