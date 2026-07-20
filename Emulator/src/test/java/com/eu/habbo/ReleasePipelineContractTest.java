package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleasePipelineContractTest {

    private static final Path RELEASE_WORKFLOW = Path.of("../.github/workflows/build-release.yml");

    @Test
    void automaticReleaseRequiresSuccessfulMainBranchCi() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);
        String triggers = workflow.substring(workflow.indexOf("on:"), workflow.indexOf("concurrency:"));

        assertTrue(triggers.contains("workflow_run:"));
        assertTrue(triggers.contains("workflows: [CI]"));
        assertTrue(triggers.contains("types: [completed]"));
        assertTrue(triggers.contains("branches: [main]"));
        assertFalse(triggers.contains("push:"));
        assertTrue(workflow.contains("github.event.workflow_run.conclusion == 'success'"));
        assertTrue(workflow.contains("github.event.workflow_run.head_branch == 'main'"));
        // A fork can open a pull request from a branch it named "main"; its CI runs
        // as a pull_request event from another repository. Releasing must be gated on
        // the run being a push from this repository, or that fork's commit could be
        // pushed to main and published as a signed release.
        assertTrue(workflow.contains("github.event.workflow_run.event == 'push'"));
        assertTrue(workflow.contains("github.event.workflow_run.head_repository.full_name == github.repository"));
    }

    @Test
    void releaseBuildRunsTheCompleteVerificationLifecycle() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);

        assertTrue(workflow.matches("(?s).*run: (?:mvn|\\./mvnw) -B clean verify.*"));
        assertFalse(workflow.contains("-DskipTests"));
        assertTrue(workflow.contains("github.event.workflow_run.head_sha"));
        assertTrue(workflow.contains("ref: ${{ env.SOURCE_SHA }}"));
    }

    @Test
    void releaseArtifactsIncludeIntegrityMetadata() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>cyclonedx-maven-plugin</artifactId>"));
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

        assertTrue(workflow.contains("release_sha=$(git rev-parse HEAD)"));
        assertTrue(workflow.contains("target_commitish: ${{ steps.commit.outputs.release_sha }}"));
    }

    @Test
    void releaseVersionCommitUsesConventionalCommitFormat() throws Exception {
        String workflow = Files.readString(RELEASE_WORKFLOW);

        assertTrue(workflow.contains("git commit -m \"chore(release): bump version to "));
    }
}
