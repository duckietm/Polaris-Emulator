package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogOfferSnapshot;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class CatalogStudioSessionComposer extends MessageComposer {
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
    private final List<CatalogOfferSnapshot> offers;

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
                List.of(),
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
            List<CatalogPageSnapshot> pages,
            List<CatalogOfferSnapshot> offers) {
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
        this.offers = List.copyOf(offers);
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
        Gson gson = new Gson();
        this.response.appendString(gson.toJson(pages));
        this.response.appendString(gson.toJson(offers));
        return this.response;
    }
}
