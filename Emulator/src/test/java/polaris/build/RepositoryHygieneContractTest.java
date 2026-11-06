package polaris.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryHygieneContractTest {

    private static final Path REPOSITORY_ROOT = Path.of("..");

    @Test
    void repositoryDefinesPortableTextAndIgnoreRules() throws Exception {
        String editorConfig = read(".editorconfig");
        assertTrue(editorConfig.contains("root = true"));
        assertTrue(editorConfig.contains("charset = utf-8"));
        assertTrue(editorConfig.contains("end_of_line = lf"));

        String attributes = read(".gitattributes");
        assertTrue(attributes.contains("* text=auto eol=lf"));
        assertTrue(attributes.contains("*.jar binary"));
        assertTrue(attributes.contains("*.png binary"));
        assertTrue(attributes.contains("*.zip binary"));

        List<String> ignoredLines = Files.readAllLines(REPOSITORY_ROOT.resolve(".gitignore"));
        assertFalse(ignoredLines.contains("*.txt"));
        assertFalse(ignoredLines.contains("*.log"));
        assertFalse(ignoredLines.contains("*.zip"));
        assertFalse(ignoredLines.contains("src/test/"));
        assertTrue(ignoredLines.contains("/logging/*.log"));
    }

    @Test
    void contributorAndPullRequestGuidanceProtectCompatibility() throws Exception {
        String contributing = read("CONTRIBUTING.md");
        assertTrue(contributing.contains("Existing plugin jars must continue to load"));
        assertTrue(contributing.contains("feature/"));
        assertTrue(contributing.contains("bugfix/"));
        assertTrue(contributing.contains("refactor/"));
        assertTrue(contributing.contains("test-first"));

        String pullRequestTemplate = read(".github/pull_request_template.md");
        assertTrue(pullRequestTemplate.contains("Compatibility"));
        assertTrue(pullRequestTemplate.contains("public API"));
        assertTrue(pullRequestTemplate.contains("plugin"));
        assertTrue(pullRequestTemplate.contains("Manual testing"));
    }

    @Test
    void buildHelpersHavePortableShellEntrypoints() throws Exception {
        assertShellScript("scripts/build-latest.sh");
        assertShellScript("scripts/build-latest.test.sh");
        assertShellScript("scripts/verify-furni-import.sh");
    }

    @Test
    void projectPoliciesAndCompilerMetadataAreDocumented() throws Exception {
        assertTrue(read("docs/architecture.md").contains("Compatibility facades"));
        assertTrue(read("docs/plugin-author-guide.md").contains("plugin.json"));
        assertTrue(read("docs/versioning-and-release.md").contains("green `verify`"));
        assertTrue(read("Emulator/pom.xml").contains("<parameters>true</parameters>"));
    }

    private static void assertShellScript(String relativePath) throws Exception {
        String script = read(relativePath);
        assertTrue(script.startsWith("#!/usr/bin/env bash"), relativePath);
        assertTrue(script.contains("set -euo pipefail"), relativePath);
    }

    private static String read(String relativePath) throws Exception {
        Path path = REPOSITORY_ROOT.resolve(relativePath);
        assertTrue(Files.isRegularFile(path), relativePath + " must exist");
        return Files.readString(path);
    }
}
