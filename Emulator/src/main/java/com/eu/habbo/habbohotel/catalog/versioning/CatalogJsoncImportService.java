package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CatalogJsoncImportService {
    private final Gson gson = new Gson();

    public CatalogImportDryRun dryRun(CatalogVersionSnapshot current, String jsonc) {
        Objects.requireNonNull(current, "current");
        CatalogJsoncDocument document = parse(jsonc);
        if (document.baseVersionId() != current.version().id()
                || document.baseRevision() != current.version().revision()) {
            throw new CatalogConcurrentModificationException(current.version().id(), document.baseRevision());
        }
        CatalogVersionSnapshot target =
                new CatalogVersionSnapshot(current.version(), document.pages(), document.offers());
        var changes = CatalogChangeSetSupport.diff(current, target, gson);
        return new CatalogImportDryRun(
                CatalogChangeSource.JSONC,
                current.version().id(),
                current.version().revision(),
                jsonc,
                changes,
                CatalogChangeSetSupport.fingerprint(
                        current.version().id(), current.version().revision(), changes, gson));
    }

    public CatalogJsoncDocument parse(String jsonc) {
        Objects.requireNonNull(jsonc, "jsonc");
        String json = stripComments(jsonc);
        rejectDuplicateKeys(json);
        try {
            CatalogJsoncDocument document = gson.fromJson(json, CatalogJsoncDocument.class);
            if (document == null) throw new IllegalArgumentException("Catalog JSONC document is empty");
            validateReferences(document);
            return document;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Catalog Studio JSONC", exception);
        }
    }

    static String stripComments(String input) {
        StringBuilder output = new StringBuilder(input.length());
        boolean inString = false;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            char next = index + 1 < input.length() ? input.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    output.append(current);
                } else output.append(' ');
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    output.append("  ");
                    index++;
                    blockComment = false;
                } else output.append(current == '\n' || current == '\r' ? current : ' ');
                continue;
            }
            if (inString) {
                output.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') {
                inString = true;
                output.append(current);
            } else if (current == '/' && next == '/') {
                output.append("  ");
                index++;
                lineComment = true;
            } else if (current == '/' && next == '*') {
                output.append("  ");
                index++;
                blockComment = true;
            } else output.append(current);
        }
        if (inString || blockComment) throw new IllegalArgumentException("Unterminated JSONC string or comment");
        return output.toString();
    }

    private static void rejectDuplicateKeys(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            readValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Unexpected content after the JSONC document");
            }
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("Invalid Catalog Studio JSONC", exception);
        }
    }

    private static void readValue(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> keys = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!keys.add(name)) throw new IllegalArgumentException("Duplicate JSON key: " + name);
                    readValue(reader);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) readValue(reader);
                reader.endArray();
            }
            case STRING -> reader.nextString();
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("Unexpected JSON token: " + reader.peek());
        }
    }

    private static void validateReferences(CatalogJsoncDocument document) {
        Set<EntityKey> pages = new HashSet<>();
        for (CatalogPageSnapshot page : document.pages()) {
            EntityKey key = new EntityKey(page.catalogType(), page.pageId());
            if (!pages.add(key)) throw new IllegalArgumentException("Duplicate page ID: " + key);
        }
        Set<EntityKey> offers = new HashSet<>();
        for (CatalogOfferSnapshot offer : document.offers()) {
            EntityKey key = new EntityKey(offer.catalogType(), offer.offerId());
            if (!offers.add(key)) throw new IllegalArgumentException("Duplicate offer ID: " + key);
            if (!pages.contains(new EntityKey(offer.catalogType(), offer.pageId()))) {
                throw new IllegalArgumentException("Offer references a missing page: " + offer.pageId());
            }
        }
    }

    private record EntityKey(CatalogPageType catalogType, int id) {}
}
