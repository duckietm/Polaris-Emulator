package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleasePipelineContractTest {

    private static final Path RELEASE_WORKFLOW =
            Path.of("../.github/workflows/build-release.yml");

    @Test
    void automaticReleaseRequiresSuccessfulMainBranchCi() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);
        String triggers = workflow.substring(
                workflow.indexOf("on:"),
                workflow.indexOf("concurrency:")
        );

        assertTrue(triggers.contains("workflow_run:"));
        assertTrue(triggers.contains("workflows: [CI]"));
        assertTrue(triggers.contains("types: [completed]"));
        assertTrue(triggers.contains("branches: [main]"));
        assertFalse(triggers.contains("push:"));
        assertTrue(workflow.contains(
                "github.event.workflow_run.conclusion == 'success'"
        ));
        assertTrue(workflow.contains(
                "github.event.workflow_run.head_branch == 'main'"
        ));
    }

    @Test
    void releaseBuildRunsTheCompleteVerificationLifecycle() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);

        assertTrue(workflow.contains("run: mvn -B clean verify"));
        assertFalse(workflow.contains("-DskipTests"));
        assertTrue(workflow.contains("github.event.workflow_run.head_sha"));
        assertTrue(workflow.contains("ref: ${{ env.SOURCE_SHA }}"));
    }

    @Test
    void releaseArtifactsIncludeIntegrityMetadata() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains(
                "<artifactId>cyclonedx-maven-plugin</artifactId>"
        ));
        assertTrue(workflow.contains("actions/attest@"));
        assertTrue(workflow.contains("sbom-path:"));
        assertTrue(workflow.contains("attestations: write"));
        assertTrue(workflow.contains("id-token: write"));
        assertTrue(workflow.contains("-sbom.json"));
        assertTrue(workflow.contains("-jar-with-dependencies.jar.sha256"));
    }

    @Test
    void releaseTagTargetsTheVerifiedVersionCommit() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);

        assertTrue(workflow.contains(
                "release_sha=$(git rev-parse HEAD)"
        ));
        assertTrue(workflow.contains(
                "target_commitish: ${{ steps.commit.outputs.release_sha }}"
        ));
    }

    @Test
    void releaseVersionCommitUsesConventionalCommitFormat() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);

        assertTrue(workflow.contains("git commit -m \"chore(release): bump version to "));
    }
}
