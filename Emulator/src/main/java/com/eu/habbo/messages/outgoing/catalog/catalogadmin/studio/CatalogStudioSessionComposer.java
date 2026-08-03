package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public final class CatalogStudioSessionComposer extends MessageComposer {
    private static final int SNAPSHOT_PAYLOAD_VERSION = 2;
    private static final String SNAPSHOT_ENCODING = "GZIP_BASE64_JSON";
    private static final int MAX_STRING_CHUNK_LENGTH = Short.MAX_VALUE;

    private final long activeVersionId;
    private final long draftVersionId;
    private final long revision;
    private final Instant activeUpdatedAt;
    private final Instant draftCreatedAt;
    private final int pendingCount;
    private final List<CatalogStudioActor> actors;
    private final boolean validationCurrent;
    private final int validationIssueCount;
    private final List<CatalogStudioPublishedVersion> publishedVersions;
    private final List<CatalogPageSnapshot> pages;

    public CatalogStudioSessionComposer(
            long activeVersionId,
            long draftVersionId,
            long revision,
            Instant activeUpdatedAt,
            Instant draftCreatedAt,
            int pendingCount,
            List<CatalogStudioActor> actors,
            boolean validationCurrent,
            int validationIssueCount,
            List<CatalogStudioPublishedVersion> publishedVersions) {
        this(
                activeVersionId,
                draftVersionId,
                revision,
                activeUpdatedAt,
                draftCreatedAt,
                pendingCount,
                actors,
                validationCurrent,
                validationIssueCount,
                publishedVersions,
                List.of());
    }

    public CatalogStudioSessionComposer(
            long activeVersionId,
            long draftVersionId,
            long revision,
            Instant activeUpdatedAt,
            Instant draftCreatedAt,
            int pendingCount,
            List<CatalogStudioActor> actors,
            boolean validationCurrent,
            int validationIssueCount,
            List<CatalogStudioPublishedVersion> publishedVersions,
            List<CatalogPageSnapshot> pages) {
        this.activeVersionId = activeVersionId;
        this.draftVersionId = draftVersionId;
        this.revision = revision;
        this.activeUpdatedAt = Objects.requireNonNull(activeUpdatedAt, "activeUpdatedAt");
        this.draftCreatedAt = Objects.requireNonNull(draftCreatedAt, "draftCreatedAt");
        this.pendingCount = pendingCount;
        this.actors = List.copyOf(actors);
        this.validationCurrent = validationCurrent;
        this.validationIssueCount = validationIssueCount;
        this.publishedVersions = List.copyOf(publishedVersions);
        this.pages = List.copyOf(pages);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioSessionComposer);
        this.response.appendInt(Math.toIntExact(activeVersionId));
        this.response.appendInt(Math.toIntExact(draftVersionId));
        this.response.appendInt(Math.toIntExact(revision));
        this.response.appendString(activeUpdatedAt.toString());
        this.response.appendString(draftCreatedAt.toString());
        this.response.appendInt(pendingCount);
        this.response.appendInt(actors.size());
        for (CatalogStudioActor actor : actors) {
            this.response.appendInt(actor.id());
            this.response.appendString(actor.username());
        }
        this.response.appendBoolean(validationCurrent);
        this.response.appendInt(validationIssueCount);
        this.response.appendInt(publishedVersions.size());
        for (CatalogStudioPublishedVersion version : publishedVersions) {
            this.response.appendInt(Math.toIntExact(version.id()));
            this.response.appendString(version.label());
            this.response.appendString(version.publishedAt().toString());
        }
        List<String> pageChunks = encodePageChunks(pages);
        this.response.appendInt(SNAPSHOT_PAYLOAD_VERSION);
        this.response.appendString(SNAPSHOT_ENCODING);
        this.response.appendInt(pageChunks.size());
        pageChunks.forEach(this.response::appendString);
        return this.response;
    }

    private static List<String> encodePageChunks(List<CatalogPageSnapshot> pages) {
        if (pages.isEmpty()) return List.of();

        byte[] json = new Gson().toJson(pages).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(json);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compress the Catalog Studio page index", exception);
        }

        String encoded = Base64.getEncoder().encodeToString(compressed.toByteArray());
        List<String> chunks = new ArrayList<>((encoded.length() / MAX_STRING_CHUNK_LENGTH) + 1);
        for (int offset = 0; offset < encoded.length(); offset += MAX_STRING_CHUNK_LENGTH) {
            chunks.add(encoded.substring(offset, Math.min(offset + MAX_STRING_CHUNK_LENGTH, encoded.length())));
        }
        return List.copyOf(chunks);
    }
}
