package com.eu.habbo.habbohotel.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Locale;

public final class FurnidataJson {

    private FurnidataJson() {}

    public static JsonObject parseObject(String content) {
        String normalized = stripJsonc(content);

        try (JsonReader reader = new JsonReader(new StringReader(normalized))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);

            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonSyntaxException("Unexpected content after JSON document");
            }

            if (!element.isJsonObject()) {
                throw new JsonSyntaxException("Furnidata document must be a JSON object");
            }

            return element.getAsJsonObject();
        } catch (IOException exception) {
            throw new JsonIOException("Failed to read JSONC document", exception);
        }
    }

    public static String stripJsonc(String content) {
        if (content == null || content.isEmpty()) return content;

        StringBuilder withoutComments = new StringBuilder(content.length());
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (inString) {
                withoutComments.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                withoutComments.append(current);
                continue;
            }

            if (current == '/' && index + 1 < content.length()) {
                char next = content.charAt(index + 1);
                if (next == '/') {
                    withoutComments.append("  ");
                    index += 2;
                    while (index < content.length()) {
                        char commentChar = content.charAt(index);
                        if (commentChar == '\r' || commentChar == '\n') {
                            index--;
                            break;
                        }
                        withoutComments.append(' ');
                        index++;
                    }
                    continue;
                }

                if (next == '*') {
                    withoutComments.append("  ");
                    index += 2;
                    boolean closed = false;
                    while (index < content.length()) {
                        char commentChar = content.charAt(index);
                        if (commentChar == '*' && index + 1 < content.length() && content.charAt(index + 1) == '/') {
                            withoutComments.append("  ");
                            index++;
                            closed = true;
                            break;
                        }
                        withoutComments.append(commentChar == '\r' || commentChar == '\n' ? commentChar : ' ');
                        index++;
                    }
                    if (!closed) throw new JsonSyntaxException("Unterminated JSONC block comment");
                    continue;
                }
            }

            withoutComments.append(current);
        }

        String stripped = withoutComments.toString();
        StringBuilder withoutTrailingCommas = new StringBuilder(stripped.length());
        inString = false;
        escaped = false;

        for (int index = 0; index < stripped.length(); index++) {
            char current = stripped.charAt(index);

            if (inString) {
                withoutTrailingCommas.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                withoutTrailingCommas.append(current);
                continue;
            }

            if (current == ',') {
                int nextIndex = index + 1;
                while (nextIndex < stripped.length() && Character.isWhitespace(stripped.charAt(nextIndex))) {
                    nextIndex++;
                }
                if (nextIndex < stripped.length()) {
                    char next = stripped.charAt(nextIndex);
                    if (next == '}' || next == ']') continue;
                }
            }

            withoutTrailingCommas.append(current);
        }

        return withoutTrailingCommas.toString();
    }

    public static boolean isSupportedDocument(Path path) {
        if (path == null || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".jsonc");
    }
}
